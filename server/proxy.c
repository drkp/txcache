// -*- c-file-style: "bsd" -*-

#include "proxy.h"

#include <stdlib.h>

void *
ServerProxy_Lookup(host_t *h, const void *key, size_t keyLen, interval_t interval,
                   pin_t earliestPin, StoreLookupPolicyId_t lookupPolicy,
                   size_t *dataLenOut, interval_t *intervalOut,
                   int *nInvalTagsOut, char ***invalTagsOut)
{
        int err;

        IOBuf_t *req = RPC_Start(h, SERVER_REQ_LOOKUP);
        IOBuf_PutBuf(req, key, keyLen);
        IOBuf_PutInt32(req, interval.lower);
        IOBuf_PutInt32(req, interval.upper);
        IOBuf_PutInt32(req, earliestPin);
        IOBuf_PutInt32(req, lookupPolicy);
        
        if ((err = RPC_Send(h))) {
                Warning("Failed to issue server lookup RPC: %s", strerror(err));
                RPC_Release(h);
                return NULL;
        }

        IOBuf_t args;
        switch (err = RPCC_Recv(h, &args)) {
        case SERVER_REP_FOUND:
        {
                const void *data = IOBuf_GetBuf(&args, dataLenOut);
                intervalOut->lower = IOBuf_GetInt32(&args);
                intervalOut->upper = IOBuf_GetInt32(&args);
                intervalOut->stillValid = (IOBuf_GetInt8(&args) != 0);
                        
                void *copy = malloc(*dataLenOut);
                if (!copy) {
                        Warning("Failed to allocate %lu bytes for lookup result",
                                (long)*dataLenOut);
                        return NULL;
                }
                memcpy(copy, data, *dataLenOut);
                *nInvalTagsOut = IOBuf_GetInt32(&args);
                if (*nInvalTagsOut > 0) {
                    *invalTagsOut = malloc(sizeof(char *) * *nInvalTagsOut);
                    Assert(*invalTagsOut != NULL);
                    for (int i = 0; i < *nInvalTagsOut; i++) {
                        const char *tag = IOBuf_GetString(&args);
                        (*invalTagsOut)[i] = malloc(strlen(tag)+1);
                        Assert((*invalTagsOut)[i] != NULL);
                        strcpy((*invalTagsOut)[i], tag);
                    }
                }
                IOBuf_GetEOB(&args);
                return copy;
        }
        case SERVER_REP_EMPTY:
                IOBuf_GetEOB(&args);
                return NULL;
        default:
                if (err < 0) {
                        PWarning("Failed to receive server lookup response");
                } else {
                        Warning("Received unknown response to server lookup: "
                                "%c", err);
                }
                RPC_Release(h);
                return NULL;
        }
}

void
ServerProxy_Put(host_t *h, const void *key, size_t keyLen, interval_t interval,
                const void *data, size_t dataLen, int nInvalTags,
                const char * const *invalTags, bool force)
{
        int err;

        IOBuf_t *req = RPC_Start(h, SERVER_REQ_PUT);
        IOBuf_PutBuf(req, key, keyLen);
        IOBuf_PutInt32(req, interval.lower);
        IOBuf_PutInt32(req, interval.upper);
        IOBuf_PutInt8(req, interval.stillValid ? 1 : 0);
        IOBuf_PutBuf(req, data, dataLen);
        IOBuf_PutInt32(req, nInvalTags);
        for (int i = 0; i < nInvalTags; i++)
                IOBuf_PutString(req, invalTags[i]);
        IOBuf_PutInt8(req, force ? 1 : 0);
        
        if ((err = RPC_Send(h))) {
                Warning("Failed to issue server put RPC: %s", strerror(err));
                RPC_Release(h);
        }
}


void
ServerProxy_Invalidate(host_t *h, pin_t pin,
                       int nInvalTags, const char * const *invalTags)
{
        int err;

        Assert(!PIN_SPECIAL(pin));
        
        IOBuf_t *req = RPC_Start(h, SERVER_REQ_INVAL);
        IOBuf_PutInt32(req, pin);
        IOBuf_PutInt32(req, nInvalTags);
        for (int i = 0; i < nInvalTags; i++)
                IOBuf_PutString(req, invalTags[i]);
        
        if ((err = RPC_Send(h))) {
                Warning("Failed to issue server invalidate RPC: %s",
                        strerror(err));
                RPC_Release(h);
        }
}
