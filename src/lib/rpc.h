// -*- c-file-style: "bsd"; indent-tabs-mode: t -*-

#ifndef _LIB_RPC_H_
#define _LIB_RPC_H_

#include <netinet/in.h>
#include <arpa/inet.h>

#include "reactor.h"
#include "iobuf.h"
#include "iobuf-getput.h"

#define debug 1

struct host_t;

typedef void (*RPC_HostCloseFn)(struct host_t *);

typedef void (*handler_t)(struct host_t*, IOBuf_t*);

typedef struct host_t {
	// The socket connected to this host.  This will be set to 0
	// if this host has been released.
	int socket;
	// Data being received from the network
	IOBuf_t in;
	// Data being sent to the network
	IOBuf_t out;
	// If this connection is non-blocking, this should be the
	// reactor entry corresponding to this connection
	Reactor_Entry_t *re;
	// The sockaddr_in of the other side, for debugging
	struct sockaddr_in sin;
	// Extra data we might want to keep about the host
	void *data;
	// Close callback
	RPC_HostCloseFn onClose;
	// Handlers for messages received by this host.  Points to the
	// handlers array in the server_t from which this connection
	// was accepted.
	handler_t *handlers;
} host_t;

typedef struct server_t {
	int socket;
	Reactor_Entry_t *re;
	handler_t handlers[256];
} server_t;

#define HOST_NULL_IZER \
	{.socket = 0, .in = IOBUF_NULL_IZER, .out = IOBUF_NULL_IZER, .re = NULL}

extern const host_t HOST_NULL;

#define FMT_RPC_HOST "%s:%d"
#define VA_RPC_HOST(h) inet_ntoa((h)->sin.sin_addr), ntohs((h)->sin.sin_port)

//RPC server stuff
void RPCS_Init(server_t *serv, const char *port);
void RPCS_RegisterHandler(server_t *serv, char rpc, handler_t handler);

//RPC client stuff
bool RPCC_Connect(const char *addr, const char *port, host_t *hostOut);
int RPCC_Recv(host_t *h, IOBuf_t *argsOut);

// Host-related functions
void RPC_Release(host_t *h);
static inline bool RPC_IsValid(host_t *h)
{
	return h->socket != 0;
}
bool RPC_Pair(server_t *serv, host_t *h1, host_t *h2);

//functions for sending RPCs
IOBuf_t *RPC_Start(host_t *h, char rpc);
int RPC_Send(host_t*);

// Internal functions
int RPC_FillIn(host_t *h);
int RPC_FlushOut(host_t *h, bool block);
bool RPC_DecodeMsg(IOBuf_t *buf, char *rpcOut, IOBuf_t *argsOut);
bool RPC_PrepareHost(int socket, bool nonblocking, host_t *hostOut);

#endif
