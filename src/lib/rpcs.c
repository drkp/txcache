// -*- c-file-style: "bsd"; indent-tabs-mode: t -*-

#include "rpc.h"

#include <stdlib.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

void
RPCS_RegisterHandler(server_t *serv, char rpc, handler_t handler) {
	if (serv->handlers[(int)rpc])
		Panic("There is already a %c handler", rpc);
        serv->handlers[(int)rpc] = handler;
}

static void
RPCSOnConnect(Reactor_Entry_t *entry, int socket, void *opaque)
{
	server_t *serv = opaque;

	//accept a connection
	host_t *h = malloc(sizeof *h);
	if (!h)
		Panic("Failed to allocate host struct");
	socklen_t sinlen = sizeof(h->sin);
	int cs = accept(socket, (struct sockaddr *)&h->sin, &sinlen);
	if (cs < 0) {
		PWarning("Failed to accept connection");
	} else if (!RPC_PrepareHost(cs, true, h)) {
		Warning("Failed to prepare host");
		free(h);
	} else {
		if (debug)
			Notice("Connection from " FMT_RPC_HOST,
			       VA_RPC_HOST(h));
		h->handlers = serv->handlers;
	}
}

static bool
RPCS_Listen(server_t *serv, const char *port) {
        int s, n;
  
        struct addrinfo hints = {
                .ai_family   = AF_INET,
                .ai_socktype = SOCK_STREAM,
                .ai_protocol = 0,
		.ai_flags    = AI_PASSIVE,
        };
        struct addrinfo *ai;
	int res;
        if ((res = getaddrinfo(NULL, port, &hints, &ai))) {
                Warning("Failed to resolve service %s: %s",
			port, gai_strerror(res));
                return false;
        }
        if (ai->ai_addr->sa_family != AF_INET)
                Panic("getaddrinfo returned a non IPv4 address");
  
        if ((s = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol)) < 0) {
                PWarning("Failed to create socket to listen");
                return false;
        }
  
        //allow the program to run again even if there are old connections
        n = 1;
        if (setsockopt(s, SOL_SOCKET, SO_REUSEADDR, (char *)&n, sizeof(n)) < 0) {
                close(s);
                return false;
        }

        if (bind(s, ai->ai_addr, ai->ai_addrlen) < 0) {
                PWarning("Failed to bind to socket to listen");
                close(s);
                return false;
        }

        if (listen(s, 5) < 0) {
                PWarning("Failed to listen on port");
                close(s);
                return false;
        }

	serv->socket = s;
	serv->re = Reactor_Add(s, RPCSOnConnect, NULL, serv);
	if (!serv->re) {
		close(s);
		return false;
	}
	Notice("Listening on %s", port);
	return true;
}

void
RPCS_Init(server_t *serv, const char *port) {
	if (port == NULL) {
		serv->socket = -1;
		serv->re = NULL;
	} else {
		if (!RPCS_Listen(serv, port)) {    
			Panic("Couldn't listen on server port!");
		}
	}

	memset(serv->handlers, 0, sizeof serv->handlers);
}

