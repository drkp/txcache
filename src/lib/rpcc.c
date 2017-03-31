// -*- c-file-style: "bsd" -*-

#define __USE_POSIX

#include "rpc.h"

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

bool
RPCC_Connect(const char *addr, const char *port, host_t *hostOut) {
        int res;
	int s;

        struct addrinfo hints = {
                .ai_family   = AF_INET,
                .ai_socktype = SOCK_STREAM,
                .ai_protocol = 0,
        };
        struct addrinfo *ai;
        if ((res = getaddrinfo(addr, port, &hints, &ai))) {
                Warning("Failed to resolve %s:%s: %s", addr, port, gai_strerror(res));
                return false;
        }
        if (ai->ai_addr->sa_family != AF_INET)
                Panic("getaddrinfo returned a non IPv4 address");
        hostOut->sin = *(struct sockaddr_in*)ai->ai_addr;

	s = socket(AF_INET, SOCK_STREAM, 0);
	if (connect(s, ai->ai_addr, ai->ai_addrlen) < 0) {
		PWarning("Failed to connect to %s:%s", addr, port);
		close(s);
                freeaddrinfo(ai);
		return false;
	}
        freeaddrinfo(ai);

	if (!RPC_PrepareHost(s, false, hostOut)) {
		Warning("Failed to prepare host %s:%s", addr, port);
		close(s);
		return false;
	}

	Notice("Connected to " FMT_RPC_HOST " (%s:%s)",
               VA_RPC_HOST(hostOut), addr, port);

	return true;
}

int
RPCC_Recv(host_t *h, IOBuf_t *argsOut) {
        if (!RPC_IsValid(h))
                Panic("Attempt to receive RPC from disconnected host");

	// Free up the previous message
	IOBuf_Shift(&h->in);
  
	while(1) {
		// Do we have a whole message yet?  We do this before
		// reading the first time in case we already have data
		// in our input buffer.
		char rpc;
		if (RPC_DecodeMsg(&h->in, &rpc, argsOut))
			return rpc;

		int err = RPC_FillIn(h);
		if (err < 0)
			return err;
                if (err == 0) {
                        errno = ECONNABORTED;
                        return -1;
                }
	}
}
