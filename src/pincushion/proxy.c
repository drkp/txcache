// -*- c-file-style: "bsd" -*-

#include "proxy.h"

#include <stdlib.h>

PinStamp_t *
PincushionProxy_Request(host_t *h, struct timeval freshness,
                        uint32_t *numPinsOut)
{
        int err;

        IOBuf_t *req = RPC_Start(h, PINCUSHION_REQ_REQUEST);
        IOBuf_PutInt64(req, freshness.tv_sec);
        IOBuf_PutInt64(req, freshness.tv_usec);
        if ((err = RPC_Send(h))) {
                Warning("Failed to issue pin cushion RPC: %s", strerror(err));
                RPC_Release(h);
                return NULL;
        }

        IOBuf_t args;
        switch (err = RPCC_Recv(h, &args)) {
        case PINCUSHION_REP_FOUND:
        {
                *numPinsOut = IOBuf_GetInt32(&args);
                PinStamp_t *pinsOut = malloc(*numPinsOut * sizeof *pinsOut);
                if (!pinsOut) {
                        Warning("Failed to allocate buffer for %u pins",
                                *numPinsOut);
                        return NULL;
                }
                for (int i = 0; i < *numPinsOut; ++i) {
                        pinsOut[i].tv.tv_sec = IOBuf_GetInt64(&args);
                        pinsOut[i].tv.tv_usec = IOBuf_GetInt64(&args);
                        pinsOut[i].pin = IOBuf_GetInt32(&args);
                }
                IOBuf_GetEOB(&args);
                return pinsOut;
        }
        case PINCUSHION_REP_EMPTY:
                *numPinsOut = 0;
                IOBuf_GetEOB(&args);
                return NULL;
        default:
                if (err < 0) {
                        PWarning("Failed to receive pin cushion response");
                } else {
                        Warning("Received unknown response from pin cushion: "
                                "%c", err);
                }
                RPC_Release(h);
                *numPinsOut = 0;
                return NULL;
        }
}

void
PincushionProxy_Insert(host_t *h, PinStamp_t pinstamp)
{
        int err;

        IOBuf_t *req = RPC_Start(h, PINCUSHION_REQ_INSERT);
        IOBuf_PutInt64(req, pinstamp.tv.tv_sec);
        IOBuf_PutInt64(req, pinstamp.tv.tv_usec);
        IOBuf_PutInt32(req, pinstamp.pin);
        if ((err = RPC_Send(h))) {
                Warning("Failed to issue pin cushion RPC: %s", strerror(err));
                RPC_Release(h);
        }
}

void
PincushionProxy_Release(host_t *h)
{
        int err;

        RPC_Start(h, PINCUSHION_REQ_RELEASE);
        if ((err = RPC_Send(h))) {
                Warning("Failed to issue pin cushion RPC: %s", strerror(err));
                RPC_Release(h);
        }
}
