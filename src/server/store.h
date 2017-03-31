// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_STORE_H_
#define _SERVER_STORE_H_

#ifndef PARANOID
#define PARANOID 0
#endif

#include "storevset.h"
#include "lookuppolicy.h"
#include "evictionpolicy.h"

#include "lib/iobuf.h"

// Per-class statistics
%instance Hashtable StoreClassStatsHash(struct StoreClassStats_t, link);
typedef struct StoreClassStats_t
{
        long lookups, hits;
        long keyMisses, stalenessMisses, capacityMisses, consistencyMisses;
        long extensions, potentialExtensions;
        long entries;
        StoreClassStatsHash_Link_t link;
} StoreClassStats_t;

typedef struct StoreInvalMapEntry_t
{
        char *tag;
        entry_t *entry;
} StoreInvalMapEntry_t;

%instance RBTree StoreInvalTree(StoreInvalMapEntry_t,
                                StoreInvalMapEntry_t);

typedef struct StoreInvalBufferEntry_t
{
        pin_t pin;
        time_t time;
        char *tag;
        bool descendants;
} StoreInvalBufferEntry_t;

%instance RBTree StoreInvalBuffer(StoreInvalBufferEntry_t,
                                  StoreInvalBufferEntry_t);

// StoreHT_t is a hash table mapping from keys to StoreVSet_t's, which
// are trees mapping from the lower bound of version intervals to
// entry_t's.
typedef struct Store_t {
        StoreHT_t lines;
        size_t keyBytes;
        size_t entryBytes;
        size_t maxBytes;
        pin_t lastInvalTime;
        StoreInvalTree_t invalMap;
        StoreInvalBuffer_t invalBuffer;
        pin_t bufferValidFrom;
        StoreLookupPolicy_t *lookupPolicyTable[NUM_LOOKUP_POLICIES];
        StoreEvictionPolicy_t *evictionPolicy;
        void *evictionPolicyState;
        bool hasEvicted;
        time_t uselessBound;
        EntryInvTimeOrder_t invTimeOrder;
        
        // Statistics
        StoreClassStats_t totalStats;
        StoreClassStatsHash_t classStats;
        bool collectStats;
} Store_t;

void _Store_RegisterLookupPolicy(StoreLookupPolicy_t *policy);
void _Store_RegisterEvictionPolicy(StoreEvictionPolicy_t *policy);
bool Store_Init(Store_t *store, size_t maxBytes, time_t uselessBound);
void Store_Release(Store_t *store);
size_t Store_Footprint(Store_t *store);
entry_t* Store_Lookup(Store_t *store,
                      const void *key, size_t keyLen, interval_t interval,
                      pin_t earliestPin,
                      StoreLookupPolicyId_t lookupPolicyId);
void Store_Flush(Store_t *store);
bool Store_Put(Store_t *store,
               const void *key, size_t keyLen, interval_t interval,
               const void *data, size_t dataLen, int nInvalTags,
               const char * const *invalTags, bool memcachedMode);

char *Store_DumpStats(Store_t *store);
void Store_PrintStats(Store_t *store);
void Store_ClearStats(Store_t *store);
void Store_Invalidate(Store_t *store, pin_t ts, int nInvalTags,
                      const char * const *invalTags);
void Store_RemoveUseless(Store_t *store);
void Store_EvictionOrderIterReset(Store_t *store);
entry_t * Store_EvictionOrderIterNext(Store_t *store);

// marshal.c

/**
 * Dump unbounded entries to the given file.
 */
void Store_Dump(Store_t *store, int fd);
/**
 * Dump full store contents to the given file.
 * (different format from Store_Dump!)
 */
void Store_DebugDump(Store_t *store, int fd);
/**
 * Reload a currently empty store from a dump.  All intervals will be
 * set to [-inf, 1)+.
 */
void Store_Load(Store_t *store, int fd);

#endif // _SERVER_STORE_H_
