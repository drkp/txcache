// -*- c-file-style: "bsd" -*-

#include "rpc.h"

#include <netinet/tcp.h>

#include "iobuf.h"
#include "iobuf-getput.h"
#include "message.h"

const host_t HOST_NULL = HOST_NULL_IZER;

void
RPC_Release(host_t *h)
{
        if (h->socket) {
                Notice("Closing connection to " FMT_RPC_HOST, VA_RPC_HOST(h));
                if (h->re)
                        Reactor_Close(h->re);
                else
                        close(h->socket);
                h->re = NULL;
                h->socket = 0;
                if (h->onClose)
                        h->onClose(h);
        }
        IOBuf_Release(&h->in);
        IOBuf_Release(&h->out);
}

IOBuf_t *
RPC_Start(host_t *h, char rpc) {
        if (IOBuf_InNested(&h->out))
                Panic("Previous RPC was not finished");
        IOBuf_PutInt8(&h->out, rpc);
        IOBuf_BeginNested(&h->out);
        return &h->out;
}

int
RPC_Send(host_t *h) {
        int err;

        if (!RPC_IsValid(h))
                Panic("Attempt to send RPC to disconnected host");

        IOBuf_EndNested(&h->out);
        err = IOBuf_Error(&h->out);
        if (err) {
                errno = err;
                PWarning("Failed to send RPC because of IOBuf error");
                return -1;
        }
        if (IOBuf_InNested(&h->out))
                Panic("Unclosed sub-buffer in RPC");

        if (h->re) {
                // Non-blocking I/O. Attempt a non-blocking flush;
                // this might cause us to mark the connection
                // writable and try later instead.
                return RPC_FlushOut(h, false);
        } else {
                // Blocking I/O.  Flush the output buffer now.
                return RPC_FlushOut(h, true);
        }
}

int
RPC_FillIn(host_t *h)
{
        const int BLOCK_SIZE = 1024;

        Assert(RPC_IsValid(h));

        void *buf = IOBuf_Reserve(&h->in, BLOCK_SIZE, __func__);
        if (!buf)
                Panic("Failed to reserve buffer space for recv");
        int n = recv(h->socket, buf, BLOCK_SIZE, 0);

        if (n < 0) {
                PWarning("Failed to receive from " FMT_RPC_HOST, VA_RPC_HOST(h));
        } else if (n == 0) {
                Debug("Read EOF from " FMT_RPC_HOST, VA_RPC_HOST(h));
        } else {
                Debug("Read %d bytes from " FMT_RPC_HOST, n, VA_RPC_HOST(h));
                IOBuf_MoveLimit(&h->in, n);
        }
        return n;
}

int
RPC_FlushOut(host_t *h, bool block) {
        Assert(RPC_IsValid(h));

        size_t remaining;
        const char *buf = IOBuf_PeekRest(&h->out, &remaining);

        Debug("Flushing %ld bytes to " FMT_RPC_HOST, (long)remaining, VA_RPC_HOST(h));

        if (remaining) {
                do {
                        int n = send(h->socket, buf, remaining,
                                     (block ? 0 : MSG_DONTWAIT));
                        if (n < 0) {
                                if (errno == EAGAIN || errno == EINTR)
                                        continue;
                                PWarning("Failed to send to " FMT_RPC_HOST, VA_RPC_HOST(h));
                                return n;
                        } else {
                                remaining -= n;
                                buf += n;
                                IOBuf_Skip(&h->out, n);
                        }
                } while (block && remaining);
                IOBuf_Shift(&h->out);
        }

        if (remaining)
                Debug("%ld bytes remain to flush", (long)remaining);

        if (remaining && h->re)
                Reactor_MarkWritable(h->re);

        // Kick the reactor loop if in eager mode/
        if (!remaining)
                Reactor_LoopIfEager();
        
        return 0;
}

bool
RPC_DecodeMsg(IOBuf_t *buf, char *rpcOut, IOBuf_t *argsOut)
{
        int pos = IOBuf_GetPos(buf);
	if (IOBuf_TryGetInt8(buf, (int8_t*)rpcOut) &&
	    IOBuf_TryGetNested(buf, argsOut)) {
		// Success!
		return true;
	} else {
		// Incomplete.  Rewind.
		IOBuf_SetPos(buf, pos);
		return false;
	}
}

static void
RPCOnReadable(Reactor_Entry_t *entry, int socket, void *opaque)
{
        host_t *h = opaque;
        int err;

        Debug(FMT_RPC_HOST " in read set", VA_RPC_HOST(h));

        err = RPC_FillIn(h);
        if (err <= 0) {
                RPC_Release(h);
        } else if (h->handlers) {
                char rpc;
                IOBuf_t args;
                bool needsShift = false;
                while (RPC_DecodeMsg(&h->in, &rpc, &args)) {
                        handler_t handler = h->handlers[(int)rpc];
                        if (debug)
                                Debug(FMT_RPC_HOST " invoking RPC %c", VA_RPC_HOST(h), rpc);
                        if (handler) {
                                handler(h, &args);
                                // The handler may have closed it
                                if (!RPC_IsValid(h))
                                        return;
                        } else {
                                Warning("Failed to find handler for rpc %c", rpc);
                        }
                        needsShift = true;
                }
                // Free up the space used by the message
                if (needsShift)
                        IOBuf_Shift(&h->in);
        }
}

static void
RPCOnWritable(Reactor_Entry_t *entry, int socket, void *opaque)
{
        host_t *h = opaque;

        Debug(FMT_RPC_HOST " in write set", VA_RPC_HOST(h));

        if (RPC_FlushOut(h, false) < 0)
                RPC_Release(h);
}

bool
RPC_PrepareHost(int socket, bool nonblocking, host_t *hostOut)
{
	int flag = 1;
	if (setsockopt(socket, IPPROTO_TCP, TCP_NODELAY,
		       (char*)&flag, sizeof flag) < 0) {
                if (errno != ENOTSUP)
                        PWarning("Failed to disable Nagle algorithm");
        }

	hostOut->socket = socket;
	if (!IOBuf_Init(&hostOut->in))
		Panic("Failed to initialize IOBuf");
	if (!IOBuf_Init(&hostOut->out))
		Panic("Failed to initialize IOBuf");
	if (nonblocking) {
		hostOut->re = Reactor_Add(socket, RPCOnReadable, RPCOnWritable,
                                          hostOut);
		if (!hostOut->re)
			return false;
	} else {
		hostOut->re = NULL;
	}
	hostOut->data = NULL;
	hostOut->onClose = NULL;
        hostOut->handlers = NULL;

	return true;
}

bool
RPC_Pair(server_t *serv, host_t *h1, host_t *h2) {
	int sockets[2];
	
	if (socketpair(AF_UNIX, SOCK_STREAM, 0, sockets) < 0) {
		Panic("Could not create socket pair");
	}

	memset(&h1->sin, 0, sizeof h1->sin);
	if (!RPC_PrepareHost(sockets[0], true, h1)) {
		Warning("Couldn't prepare first host in RPC pair");
		return false;
	}
	h1->handlers = serv->handlers;

	memset(&h2->sin, 0, sizeof h2->sin);
	if (!RPC_PrepareHost(sockets[1], true, h2)) {
		Warning("Couldn't prepare second host in RPC pair");
		return false;
	}
	return true;
}
