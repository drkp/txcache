// -*- c-file-style: "bsd"; indent-tabs-mode: t -*-

#include "invald.h"
#include "lib/rpc.h"
#include "lib/dbconn.h"
#include "lib/interval.h"
#include "server/proxy.h"

#include <stdlib.h>
#include <signal.h>

#define INVALIDATION_LISTEN_TAG "invalidation"

static Reactor_Entry_t *dbReactor;

static void InvaldOnPGReadable(struct Reactor_Entry_t *re, int fd,
			       void *opaque);
static void InvaldOnPGWritable(struct Reactor_Entry_t *re, int fd,
			       void *opaque);
static void InvaldProcessNotification();

/*
 * Note: this code uses the reactor interface for waiting for
 * notifications and for communicating with the cache nodes. However,
 * all of its interactions with Postgres are synchronous  (except the
 * asynchronous notifications, of course).
 */

struct CacheNode_t;
%instance DblList CacheNodeList(struct CacheNode_t, link);

typedef struct CacheNode_t
{
	host_t host;
	CacheNodeList_Link_t link;
} CacheNode_t;
%instance DblListImpl CacheNodeList(struct CacheNode_t, link);

struct Invalidation_t;
%instance DblList InvalidationList(struct Invalidation_t, link);

typedef struct Invalidation_t
{
	const char *tag;
	InvalidationList_Link_t link;
} Invalidation_t;
%instance DblListImpl InvalidationList(struct Invalidation_t, link);

static CacheNodeList_t cacheNodes;

void
Invald_Setup(const char *pgconninfo, FILE *nodesFile)
{
    PGresult *res;
    char nodeBuf[1024];
    char *host;
    char *port;

    signal(SIGSEGV, PanicOnSignal);

    DB_Init(pgconninfo);

    Notice("Starting invalidation distribution daemon");

    /* Start listening on the asynchronous notification tag */
    res = PQexec(db_conn, "LISTEN " INVALIDATION_LISTEN_TAG);
    if (PQresultStatus(res) != PGRES_COMMAND_OK)
	    Panic("LISTEN command failed: %s", PQerrorMessage(db_conn));
    PQclear(res);

    /* Add reactor entry */
    int ps = PQsocket(db_conn);
    if (ps <= 0)
	    Panic("Got invalid Postgres socket %d", ps);
    dbReactor = Reactor_Add(ps, InvaldOnPGReadable, InvaldOnPGWritable, NULL);
    if (!dbReactor)
	    Panic("Failed to add Postgres connection to reactor");

    /*
     * Discard first two lines of nodes file. They are the pin policy
     * and the pincushion host/port.
     */
    for (int i = 0; i < 2; i++)
    {
	    if (fgets(nodeBuf, sizeof(nodeBuf), nodesFile) == NULL)
		    Panic("Could not parse nodes file");
    }

    CacheNodeList_Init(&cacheNodes);
    
    while ((fgets(nodeBuf, sizeof(nodeBuf), nodesFile)) != NULL)
    {
	    char *sep = nodeBuf;
	    CacheNode_t *node;
	    node = malloc(sizeof(CacheNode_t));
	    if (node == NULL)
		    Panic("Allocation failed");
	    
	    host = strsep(&sep, ":");
	    port = strsep(&sep, "\n");
	    if (port == NULL)
		    Panic("Could not parse nodes file");

	    if (!RPCC_Connect(host, port, &node->host))
	    {
		    Warning("Failed to connect to %s:%s; skipping",
			    host, port);
		    free(node);
		    continue;
	    }

	    CacheNodeList_PushBack(&cacheNodes, node);
    }

    /* Make sure we connected to at least one cache node */
    if (cacheNodes.head == NULL)
	    Panic("Could not connect to any cache nodes");
}

static void
InvaldOnPGReadable(struct Reactor_Entry_t *re, int fd, void *opaque)
{
	PGnotify *notify;
	
	PQconsumeInput(db_conn);

	while ((notify = PQnotifies(db_conn)) != NULL)
	{
		Debug("Received Postgres async notification on %s "
		      "from backend %d", notify->relname, notify->be_pid);
		
		InvaldProcessNotification();
		PQfreemem(notify);
	}
}

static void
InvaldOnPGWritable(struct Reactor_Entry_t *re, int fd, void *opaque)
{
	/*
	 * We shouldn't be marking the database connection writable,
	 * so this should never get called. Panicking is probably
	 * overly paranoid, but...
	 */
	Panic("InvaldOnPGWritable unexpectedly called");
}

static void
SendInvalidationNotice(pin_t pin, InvalidationList_t *invals)
{
	Invalidation_t *inval;
	CacheNode_t *node;
	int cnt = 0;
	int i;
	const char **invalTags;

	for (inval = invals->head; inval; inval = inval->link.next)
		cnt++;

	invalTags = malloc(sizeof(char *) * cnt);
	for (i = 0, inval = invals->head;
	     inval; i++, inval = inval->link.next)
	{
		if (inval->tag == NULL)
		{
			Assert(cnt == 1);
			cnt = 0;
		}
		else
			invalTags[i] = inval->tag;
	}

	/* Send invalidation to all clients */
	for (node = cacheNodes.head; node; node = node->link.next)
		ServerProxy_Invalidate(&node->host, pin, cnt,
				       ((cnt == 0) ? NULL : invalTags));

	free(invalTags);
}

static void
InvaldProcessNotification()
{
	PGresult *res;
	InvalidationList_t invals;
	pin_t lastPin = PIN_INVALID;
	Invalidation_t *inval;

	res = PQexec(db_conn,
		     "SELECT xstamp, tag FROM pg_invalidations");
	if (PQresultStatus(res) != PGRES_TUPLES_OK)
		Panic("SELECT from pg_invalidations failed: %s",
		      PQerrorMessage(db_conn));

	if (PQntuples(res) == 0)
	{
		/*
		 * No tuples found; this can happen due to concurrent
		 * notifications and is perfectly fine.
		 */
		PQclear(res);
		return;
	}
	
	InvalidationList_Init(&invals);
	
	for (int i = 0; i < PQntuples(res); i++)
	{
		const char *tag;
		pin_t pin;
		char *pinptr;

		pinptr = PQgetvalue(res, i, 0);
		pin = strtol(pinptr, NULL, 10);
		tag = PQgetisnull(res, i, 1) ? NULL : PQgetvalue(res, i, 1);

		Debug("Got invalidation for %s at time " FMT_PIN,
		      tag, VA_PIN(pin));
		Assert(!PIN_SPECIAL(pin));

		if ((pin != lastPin) && (lastPin != PIN_INVALID))
		{
			SendInvalidationNotice(lastPin, &invals);

			while (invals.head != NULL)
			{
				inval = invals.head;
				InvalidationList_Unlink(&invals, inval);
				free(inval);
			}
		}

		lastPin = pin;

		inval = malloc(sizeof(Invalidation_t));
		Assert(inval != NULL);
		inval->tag = tag;
		InvalidationList_PushBack(&invals, inval);
	}

	SendInvalidationNotice(lastPin, &invals);

	while (invals.head != NULL)
	{
		inval = invals.head;
		InvalidationList_Unlink(&invals, inval);
		free(inval);
	}

	PQclear(res);
}
