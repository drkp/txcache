// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_EVICTIONPOLICY_H
#define _SERVER_EVICTIONPOLICY_H

#include "lib/interval.h"
#include "storevset.h"

struct Store_t;

typedef struct StoreEvictionPolicy_t
{
        const char *name;
        struct StoreEvictionPolicy_t *next;

        void *(*init)(struct Store_t *store); // returns cookie
        void (*release)(void *cookie);
        void (*hit)(void *cookie, entry_t *entry);
        void (*put)(void *cookie, entry_t *entry, bool extension);
        void (*invalidate)(void *cookie, entry_t *entry);
        void (*remove)(void *cookie, entry_t *entry);
        entry_t *(*evict)(void *cookie, size_t toFree);
        void (*iterReset)(void *cookie);
        entry_t *(*iterNext)(void *cookie);
} StoreEvictionPolicy_t;

#define STORE_REGISTER_EVICTION_POLICY(policy)                          \
        static __attribute__((constructor)) void _##policy##_register(void) \
        {                                                               \
                _Store_RegisterEvictionPolicy(&policy);                 \
        }

#endif // _SERVER_EVICTIONPOLICY_H

