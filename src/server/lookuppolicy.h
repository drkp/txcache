// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_LOOKUPPOLICY_H
#define _SERVER_LOOKUPPOLICY_H

#include "storevset.h"

#define LOOKUP_POLICY_DEFAULT 0
typedef enum StoreLookupPolicyId_t {
        LOOKUP_POLICY_LATEST = LOOKUP_POLICY_DEFAULT,
        LOOKUP_POLICY_OLDEST,   /* not implemented yet */
        LOOKUP_POLICY_SCREW_CONSISTENCY,
        NUM_LOOKUP_POLICIES
} StoreLookupPolicyId_t;

typedef enum LookupOutcome_t {
        LOOKUP_HIT = 0,
//        LOOKUP_KEY_MISS,   // never returned
        LOOKUP_STALENESS_MISS,
        LOOKUP_CAPACITY_MISS,
        LOOKUP_CONSISTENCY_MISS
} LookupOutcome_t;

struct Store_t;

typedef struct StoreLookupPolicy_t
{
        const char *name;
        StoreLookupPolicyId_t id;
        struct StoreLookupPolicy_t *next;
        LookupOutcome_t (*lookup)(struct Store_t *store, StoreVSet_t *vset,
                                  interval_t interval, pin_t earliestPin,
                                  entry_t **result);
} StoreLookupPolicy_t;

#define STORE_REGISTER_LOOKUP_POLICY(policy)                            \
        static __attribute__((constructor)) void _##policy##_register(void) \
        {                                                               \
                _Store_RegisterLookupPolicy(&policy);                   \
        }

#endif // _SERVER_LOOKUPPOLICY_H
