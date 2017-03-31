// -*- c-file-style: "bsd"; indent-tabs-mode: t -*-

#include "pincushion.h"

#include "pintable.h"
#include "proto.h"
#include "lib/rpc.h"
#include "lib/timeval.h"
#include <signal.h>

static host_t sig_client;
static host_t sig_server;

typedef struct
{
	bool hasReferencedPin;
	pin_t referencedPin;
} PC_hostinfo_t;

static void
ReleaseHostPin(host_t *h)
{
	if (h->data) {
		PC_hostinfo_t *hi = (PC_hostinfo_t*)(h->data);

		if (hi->hasReferencedPin) {
			PT_Entry_t *e = PT_FindPin(hi->referencedPin);
			PT_RemoveRef(e);
			hi->hasReferencedPin = false;
		}
	}
}

static void
HandleConnectionClosed(host_t *h)
{
	ReleaseHostPin(h);
	free(h->data);
}

static void
HandleRequest(host_t *h, IOBuf_t *args)
{
	struct timeval freshness;
	freshness.tv_sec = IOBuf_GetInt64(args);
	freshness.tv_usec = IOBuf_GetInt64(args);
	IOBuf_GetEOB(args);

	Debug("Request for pin within %ld:%ld from " FMT_RPC_HOST,
	      freshness.tv_sec, freshness.tv_usec, VA_RPC_HOST(h));

	PC_hostinfo_t *hi = (PC_hostinfo_t *) h->data;
	Assert((hi == NULL) || !hi->hasReferencedPin);

	int num_pins;
	PT_Entry_t *e = PT_Find(freshness, &num_pins);
	if (num_pins) {
		//keep track of which pin this host references
		if (h->data == NULL) {
			h->data = malloc(sizeof(PC_hostinfo_t));
			hi = (PC_hostinfo_t *) h->data;
		}
		hi->hasReferencedPin = true;
		hi->referencedPin = e->pin;
		h->onClose = HandleConnectionClosed;
		//add a reference to the pin for this host
		PT_AddRef(e);

		Debug("=> Found %d pins, earliest " FMT_PT_ENTRY,
		      num_pins, XVA_PT_ENTRY(e));
		
		//Reply to the client with the set of pins newer 
		//than the freshness requirement
		IOBuf_t *resp = RPC_Start(h, PINCUSHION_REP_FOUND);
		if (!resp)
			Panic("Failed to start pin request response");
		IOBuf_PutInt32(resp,num_pins);
		int i;
		for (i = 0; i < num_pins; i++) {
			// Reverse the index so that pins will be in
			// numeric order
			PT_Entry_t *p = PT_GetEntry(num_pins - i - 1);
			IOBuf_PutInt64(resp,p->timestamp.tv_sec);
			IOBuf_PutInt64(resp,p->timestamp.tv_usec);
			IOBuf_PutInt32(resp,(uint32_t)p->pin);
		}
		if (RPC_Send(h) < 0) {
			RPC_Release(h);
			PWarning("Failed to send pin request response");
		}
	} else {
		Debug("=> No pins found");
		IOBuf_t *resp = RPC_Start(h, PINCUSHION_REP_EMPTY);
		if (!resp)
			Panic("Failed to start pin request response");
		if (RPC_Send(h) < 0) {
			RPC_Release(h);
			PWarning("Failed to send pin request response");
		}
	}
}

static void
HandleInsert(host_t *h, IOBuf_t *args)
{
	pin_t pin;
	struct timeval timestamp;
	timestamp.tv_sec = IOBuf_GetInt64(args);
	timestamp.tv_usec = IOBuf_GetInt64(args);
	pin = IOBuf_GetInt32(args);
	IOBuf_GetEOB(args);

	Debug(FMT_RPC_HOST " inserting pin " FMT_PIN " at " FMT_TIMEVAL_ABS,
	      VA_RPC_HOST(h), VA_PIN(pin), XVA_TIMEVAL_ABS(timestamp));

	PT_Entry_t* e = PT_Insert(timestamp, pin);
	PC_hostinfo_t *hi = (PC_hostinfo_t *) h->data;
	if (h->data == NULL) {
		h->data = malloc(sizeof(PC_hostinfo_t));
		hi = (PC_hostinfo_t *) h->data;
		hi->hasReferencedPin = false;
	}

	if (!hi->hasReferencedPin) {
		hi->hasReferencedPin = true;
		hi->referencedPin = e->pin;
		h->onClose = HandleConnectionClosed;
		
		PT_AddRef(e);		
	}

#if 1
	// Print the pin table periodically
	{
		static time_t lastPrint;
		time_t now = time(NULL);
		if (now - lastPrint > 15) {
			lastPrint = now;
			PT_Print();
		}
	}
#endif
}

static void
HandleRelease(host_t *h, IOBuf_t *args)
{
	Debug("Releasing host pins for " FMT_RPC_HOST, VA_RPC_HOST(h));
	ReleaseHostPin(h);
}

void
HandleSig(host_t *h, IOBuf_t *args)
{
	int sig = IOBuf_GetInt32(args);
	IOBuf_GetEOB(args);
	switch (sig) {
	case SIGUSR1: PT_Print(); break;
	case SIGUSR2: PT_Flush(); break;
	case SIGINT:
		Warning("Received SIGINT");
#if PPROF
		// See server.c
		extern void _Z12ProfilerStopv();
		_Z12ProfilerStopv();
#endif
		signal(SIGINT, SIG_DFL);
		kill(getpid(), SIGINT);
	}
}

void
SendSigRPC(int sig)
{
	IOBuf_t *resp = RPC_Start(&sig_client, PINCUSHION_REQ_SIG);
	if (!resp)
		Panic("Failed to start pin signal message");
	IOBuf_PutInt32(resp,sig);
	if (RPC_Send(&sig_client) < 0) {
		PWarning("Failed to send signal");
	}
}

void
Pincushion_Setup(server_t *server, const char *pgconninfo,
		 struct timeval maxStaleness)
{
	//set up database connection
	DB_Init(pgconninfo);

	Notice("Starting pincushion with max staleness %ld",
	       maxStaleness.tv_sec);
	
	PT_Init(maxStaleness);
	
	RPCS_RegisterHandler(server, PINCUSHION_REQ_REQUEST, HandleRequest);
	RPCS_RegisterHandler(server, PINCUSHION_REQ_INSERT,  HandleInsert);
	RPCS_RegisterHandler(server, PINCUSHION_REQ_RELEASE, HandleRelease);
	RPCS_RegisterHandler(server, PINCUSHION_REQ_SIG, HandleSig);

	//set up connection to local host for dispatching signals
	if (!RPC_Pair(server, &sig_server, &sig_client)) {
		Panic("Could not get a socket pair!");
	}

	//set up signals
	signal(SIGUSR1, SendSigRPC);
	signal(SIGUSR2, SendSigRPC);
	signal(SIGINT, SendSigRPC);
	signal(SIGSEGV, PanicOnSignal);
}
