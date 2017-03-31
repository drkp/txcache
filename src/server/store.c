// -*- c-file-style: "bsd" -*-

#include "store.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "lib/message.h"
#include "lib/memory.h"
#include "lib/assert.h"

#define INVAL_BUFFER_TIME 30    /* seconds */

%instance HashtableImpl StoreClassStatsHash(struct StoreClassStats_t, link);

static void
StoreInvalTree_InitValue(StoreInvalMapEntry_t *val,
                         StoreInvalMapEntry_t key)
{
        *val = key;
        Assert(key.tag != NULL);
}

static void
StoreInvalTree_ReleaseValue(StoreInvalMapEntry_t *val)
{
        // Do not need to free val->tag; it's a pointer into the
        // entry_t and will get destroyed when it's released.
}

static int
StoreInvalTree_CompareValue(StoreInvalMapEntry_t *val,
                            StoreInvalMapEntry_t key)
{
        int i = strcmp(val->tag, key.tag);
        if (i != 0)
                return i;
        if (val->entry < key.entry)
                return -1;
        if (val->entry == key.entry)
                return 0;
        return 1;
}

%instance RBTreeImpl StoreInvalTree(StoreInvalMapEntry_t,
                                    StoreInvalMapEntry_t,
                                    StoreInvalTree_InitValue,
                                    StoreInvalTree_ReleaseValue,
                                    StoreInvalTree_CompareValue);

static void
StoreInvalBuffer_InitValue(StoreInvalBufferEntry_t *val,
                           StoreInvalBufferEntry_t key)
{
        *val = key;
}

static void
StoreInvalBuffer_ReleaseValue(StoreInvalBufferEntry_t *val)
{
        free(val->tag);
}

static int
StoreInvalBuffer_CompareValue(StoreInvalBufferEntry_t *val,
                              StoreInvalBufferEntry_t key)
{
        if (val->pin < key.pin)
                return -1;
        if (val->pin > key.pin)
                return 1;
        if (key.tag == NULL)
                return -1;
        return strcmp(val->tag, key.tag);
}

%instance RBTreeImpl StoreInvalBuffer(StoreInvalBufferEntry_t,
                                      StoreInvalBufferEntry_t,
                                      StoreInvalBuffer_InitValue,
                                      StoreInvalBuffer_ReleaseValue,
                                      StoreInvalBuffer_CompareValue);

static char **
Store_CopyInvalTags(int nInvalTags, const char * const *invalTags,
                    size_t *invalTagsLen)
{
        if (nInvalTags == 0)
        {
                *invalTagsLen = 0;
                return NULL;
        }

        size_t totalSize = nInvalTags * sizeof(char *);
        for (int i = 0; i < nInvalTags; i++)
                totalSize += strlen(invalTags[i]) + 1;
        *invalTagsLen = totalSize;

        char *out = malloc(totalSize);
        if (out == NULL)
                return NULL;

        char *ptr = out + nInvalTags * sizeof(char *);
        char **idx = (char **) out;
        for (int i = 0; i < nInvalTags; i++)
        {
                size_t len = strlen(invalTags[i])+1;
                idx[i] = ptr;
                memcpy(ptr, invalTags[i], len);
                ptr += len;
        }
        Assert(ptr == (out + totalSize));

        return idx;
}

static bool
Store_RegisterInvalTags(Store_t *store, entry_t *entry)
{
        for (int i = 0; i < entry->nInvalTags; i++)
        {
                StoreInvalMapEntry_t mapEntry;
                
                mapEntry.entry = entry;
                mapEntry.tag = entry->invalTags[i];
                if (!StoreInvalTree_Insert(&store->invalMap, mapEntry))
                        return false;
        }

        return true;
}

static bool
Store_UnregisterInvalTags(Store_t *store, entry_t *entry)
{
        for (int i = 0; i < entry->nInvalTags; i++)
        {
                StoreInvalMapEntry_t mapEntry;
                
                mapEntry.entry = entry;
                mapEntry.tag = entry->invalTags[i];
                if (!StoreInvalTree_Remove(&store->invalMap, mapEntry))
                        return false;
        }

        return true;
}

static void
Store_TruncateUnboundedInterval(Store_t *store, entry_t *entry, pin_t now)
{
        Assert(entry != NULL);
        Assert(entry->interval.stillValid);

        entry->interval.stillValid = false;

        Debug("Truncating unbounded interval of " FMT_ENTRY
              " at " FMT_PIN, VA_ENTRY(entry), VA_PIN(now));

        if (now > entry->interval.upper) 
                entry->interval.upper = now;

        entry->timeInvalidated = time(NULL);
        EntryInvTimeOrder_PushFront(&store->invTimeOrder,
                                    entry);

        store->evictionPolicy->invalidate(store->evictionPolicyState,
                                          entry);

        Store_UnregisterInvalTags(store, entry);
}

static StoreLookupPolicy_t *lookupPolicyHead = NULL;
static StoreEvictionPolicy_t *evictionPolicyHead = NULL;

void
_Store_RegisterLookupPolicy(StoreLookupPolicy_t *policy)
{
        if (policy->next)
                Panic("Store lookup policy '%s' registered twice",
                      policy->name);
        policy->next = lookupPolicyHead;
        lookupPolicyHead = policy;
}

void
_Store_RegisterEvictionPolicy(StoreEvictionPolicy_t *policy)
{
        if (policy->next)
                Panic("Store eviction policy '%s' registered twice",
                      policy->name);
        policy->next = evictionPolicyHead;
        evictionPolicyHead = policy;
}

static StoreEvictionPolicy_t *
_Store_GetEvictionPolicy(const char *name)
{
        StoreEvictionPolicy_t *p;
        for (p = evictionPolicyHead; p; p = p->next)
                if (strcasecmp(name, p->name) == 0)
                        return p;
        return NULL;
}

bool
Store_Init(Store_t *store, size_t maxBytes, time_t uselessBound)
{
        if (!StoreHT_Init(&store->lines))
                return false;
        store->keyBytes = 0;
        store->entryBytes = 0;
        store->maxBytes = maxBytes;
        store->lastInvalTime = PIN_NEG_INF;
        store->collectStats = true;
        store->bufferValidFrom = PIN_NEG_INF;
        store->evictionPolicy = _Store_GetEvictionPolicy("lru"); /* XXX */
        store->evictionPolicyState = store->evictionPolicy->init(store);
        store->hasEvicted = false;
        store->uselessBound = uselessBound;
        EntryInvTimeOrder_Init(&store->invTimeOrder);
        
        memset(&store->totalStats, 0, sizeof store->totalStats);
        if (!StoreClassStatsHash_Init(&store->classStats)) {
                StoreHT_Release(&store->lines);
                return false;
        }

        if (!StoreInvalTree_Init(&store->invalMap))
        {
                StoreHT_Release(&store->lines);
                StoreClassStatsHash_Release(&store->classStats);
                return false;
        }

        if (!StoreInvalBuffer_Init(&store->invalBuffer))
        {
                StoreHT_Release(&store->lines);
                StoreClassStatsHash_Release(&store->classStats);
                StoreInvalTree_Release(&store->invalMap);
                return false;
        }

        // Build lookup policy table
        for (int i = 0; i < NUM_LOOKUP_POLICIES; i++)
        {
                store->lookupPolicyTable[i] = NULL;                
        }
        for (StoreLookupPolicy_t *ptr = lookupPolicyHead;
             ptr != NULL; ptr = ptr->next)
        {
                StoreLookupPolicyId_t id = ptr->id;
                if (store->lookupPolicyTable[id] != NULL)
                        Panic("Multiple lookup policies with id %d: "
                              "'%s' and '%s'", id, ptr->name,
                              store->lookupPolicyTable[id]->name);
                store->lookupPolicyTable[id] = ptr;
        }
        
        if (store->lookupPolicyTable[LOOKUP_POLICY_DEFAULT] == NULL)
                Panic("No default lookup policy found");
        return true;
}

void
Store_Release(Store_t *store)
{
        // XXX StoreVSetTree_Release and free keys of all entries first
        //StoreHT_Release(&store->lines);
        // XXX Release stats
}

size_t
Store_Footprint(Store_t *store)
{
        size_t htFootprint;
        StoreHT_Size(&store->lines, &htFootprint);
        return htFootprint + store->keyBytes + store->entryBytes;
}

static size_t
Store_EntryFootprint(size_t dataLen, size_t invalTagsLen)
{
        return (MALLOC_SIZE(StoreVSetTree_ElemSize()) +
                MALLOC_SIZE(dataLen) + MALLOC_SIZE(invalTagsLen));
}

static void
Store_AssertSize(Store_t *store)
{
#if PARANOID
        size_t keyBytes = 0;
        size_t entryBytes = 0;

        StoreHT_Iter_t htiter;
        StoreVSet_t *vset;
        StoreVSetTree_Iterator_t *vsetiter;
        
        StoreHT_Iter(&store->lines, &htiter);

        while ((vset = StoreHT_IterNext(&htiter)) != NULL) {
                keyBytes += MALLOC_SIZE(vset->linesLink.keyLen);
                
                if (StoreVSetTree_Size(&vset->versions) > 0) {
                        vsetiter = StoreVSetTree_First(&vset->versions);
                        do
                        {
                                entry_t *entry =
                                        StoreVSetTree_Iterator_Value(vsetiter);
                                entryBytes += Store_EntryFootprint(
                                        entry->dataLen,
                                        entry->invalTagsLen);
                        } while (StoreVSetTree_Iterator_Next(vsetiter));
                        StoreVSetTree_Iterator_Release(vsetiter);
                }
        }

        Assert(keyBytes == store->keyBytes);
        Assert(entryBytes == store->entryBytes);
        // Assert(Store_Footprint(store) < store->maxBytes);
#endif  
}

static StoreClassStats_t*
Store_GetClassStats(Store_t *store, const void *key)
{
        // XXX This is a hack based on the phpclient key encoding
        const void *cls = (char*)key + 4;
        int clsLen = *(int*)key;

        StoreClassStats_t *cs =
                StoreClassStatsHash_Get(&store->classStats, cls, clsLen);
        if (!cs) {
                cs = malloc(sizeof *cs);
                if (!cs)
                        Panic("Failed to allocate class stats");
                memset(cs, 0, sizeof *cs);
                void *clsCopy = malloc(clsLen);
                if (!clsCopy)
                        Panic("Failed to allocate class name copy");
                memcpy(clsCopy, cls, clsLen);
                StoreClassStatsHash_Insert(&store->classStats, cs,
                                           clsCopy, clsLen);
        }
        return cs;
}

#define MODSTAT(store, key, stat, postop)                               \
        do {                                                            \
                if (store->collectStats) {                              \
                        (store)->totalStats.stat postop;                \
                        Store_GetClassStats(store, key)->stat postop;   \
                }                                                       \
        } while (0)


entry_t*
Store_Lookup(Store_t *store, const void *key, size_t keyLen,
             interval_t interval, pin_t earliestPin,
             StoreLookupPolicyId_t lookupPolicyId)
{
        Debug("Looking up key " FMT_VBLOB " at " FMT_INTERVAL,
              XVA_VBLOB(key, keyLen), VA_INTERVAL(interval));

        MODSTAT(store, key, lookups, ++);
        // XXX Testing data mismatch bug
        //return NULL;

        StoreVSet_t *vset = StoreHT_Get(&store->lines, key, keyLen);
        if (!vset) {
                Debug("=> Not found (key miss)");
                MODSTAT(store, key, keyMisses, ++);
                return NULL;
        }

        // Find the lookup policy
        StoreLookupPolicy_t *policy =
                store->lookupPolicyTable[lookupPolicyId];
        if (policy == NULL) {
                Warning("Lookup policy id %d not found", lookupPolicyId);
                return NULL;
        }

        // Invoke the lookup policy
        entry_t *entry;
        LookupOutcome_t outcome =
                policy->lookup(store, vset, interval, earliestPin, &entry);

        switch (outcome)
        {
        case LOOKUP_HIT:
                store->evictionPolicy->hit(store->evictionPolicyState,
                                           entry);                
                Debug("=> Hit");
                MODSTAT(store, key, hits, ++);
                entry->hitCount++;
                return entry;

        case LOOKUP_STALENESS_MISS:
                Debug("=> Not found (staleness miss)");
                MODSTAT(store, key, stalenessMisses, ++);
                return NULL;

        case LOOKUP_CAPACITY_MISS:
                Debug("=> Not found (capacity miss)");
                MODSTAT(store, key, capacityMisses, ++);
                return NULL;
                
        case LOOKUP_CONSISTENCY_MISS:
                Debug("=> Not found (consistency miss)");
                MODSTAT(store, key, consistencyMisses, ++);
                return NULL;

        default:
                NOT_REACHABLE();
        }
}

static size_t
Store_RemoveEntry(Store_t *store, entry_t *entry)
{
        Debug("Removing " FMT_ENTRY, VA_ENTRY(entry));

        size_t entryFootprint =
                Store_EntryFootprint(entry->dataLen,
                                     entry->invalTagsLen);

        // Accounting
        Assert(store->entryBytes >= entryFootprint);
        store->entryBytes -= entryFootprint;
        MODSTAT(store, entry->vset->linesLink.key, entries, --);

        if (!Store_UnregisterInvalTags(store, entry))
                return 0;

        if (!entry->interval.stillValid) {
                EntryInvTimeOrder_Unlink(&store->invTimeOrder, entry);
        } else {
#if PARANOID
                assert(entry->invTimeOrder.list == NULL);
#endif
        }
        
        
        store->evictionPolicy->remove(store->evictionPolicyState,
                                      entry);
                                      
        if (!StoreVSetTree_Remove(&entry->vset->versions,
                                  entry->interval.lower))
                return 0;
                                      
        return entryFootprint;
}

bool
Store_Evict(Store_t *store, size_t toFree)
{
        if (!store->hasEvicted) {
                store->hasEvicted = true;
                Notice("Store has reached eviction");
        }
        
        size_t freed = 0;
        while (toFree > freed) {
                entry_t *oldest =
                        store->evictionPolicy->evict(store->evictionPolicyState,
                                                     (toFree - freed));
                if (!oldest) {
                        Warning("Evicted everything, but only able to free %lu "
                                "bytes; not the requested %lu",
                                (long unsigned)freed, (long unsigned)toFree);
                        Store_PrintStats(store);
                        return false;
                }
                size_t entryFootprint;
                
                Debug("Evicting " FMT_VBLOB ", " FMT_ENTRY,
                      XVA_VBLOB(oldest->vset->linesLink.key,
                                oldest->vset->linesLink.keyLen),
                      VA_ENTRY(oldest));

                if (!(entryFootprint = Store_RemoveEntry(store, oldest)))
                        Panic("Failed to remove entry " FMT_ENTRY
                              " from vset",
                              VA_ENTRY(oldest));
                
                // XXX Free version set
                freed += entryFootprint;
        }

        Store_AssertSize(store);

        return true;
}

void
Store_Flush(Store_t *store)
{
        // Not the most efficient implementation, but simple.  We only
        // evict entryBytes because we don't currently evict keys.
        Store_Evict(store, store->entryBytes);
}

static void
Store_InvalidateTag(Store_t *store, const char *tag, bool descendants)
{
        StoreInvalTree_Iterator_t *iter;
        StoreInvalMapEntry_t searchKey;
        size_t tagLen = strlen(tag);

        Debug("Invalidating tag %s%s", tag,
              descendants ? " and descendants" : "");

        // Safe to discard const qualifier here
        searchKey.tag = (char *)tag;
        // This is <= any valid pointer
        memset(&searchKey.entry, 0, sizeof(entry_t *));

        iter = StoreInvalTree_FindIter(&store->invalMap, searchKey,
                                       StoreInvalTree_GE);
        if (!iter)
                return;   // no entry >= tag found

        bool hadNext = true;
        while (hadNext)
        {
                StoreInvalMapEntry_t *mapEntry =
                        StoreInvalTree_Iterator_Value(iter);
                // We are about to delete every entry in the map with
                // the given 'entry' field, so increment the iterator
                // until we find the next element with a different
                // entry (or hit the end of the list)
                do
                {
                        hadNext = StoreInvalTree_Iterator_Next(iter);
                } while (hadNext &&
                         (StoreInvalTree_Iterator_Value(iter)->entry ==
                          mapEntry->entry));

                if ((!descendants &&
                     (strcmp(mapEntry->tag, tag) != 0)) ||
                    (descendants &&
                     (strncmp(mapEntry->tag, tag, tagLen) != 0)))
                {
                        // We've exhausted all entries w/ matching tag
                        break;
                }
                Store_TruncateUnboundedInterval(store, mapEntry->entry,
                                                store->lastInvalTime);
        }

        StoreInvalTree_Iterator_Release(iter);

        /* Add invalidation to buffer */
        StoreInvalBufferEntry_t bufferEnt;
        bufferEnt.pin = store->lastInvalTime;
        bufferEnt.time = time(NULL);
        bufferEnt.tag = malloc(strlen(tag)+1);
        bufferEnt.descendants = descendants;
        strcpy(bufferEnt.tag, tag);

        /* Failures are OK -- tag might be a duplicate */
        (void) StoreInvalBuffer_Insert(&store->invalBuffer, bufferEnt);
}

static void
Store_InvalidateTagAndAncestorsDescendants(Store_t *store, const char *tag)
{
        char buf[strlen(tag)+1];
        strcpy(buf, tag);

        char *ptr = buf;
        char tmp;
        while (1)
        {
                if (*ptr == ':')
                {
                        ptr++;
                        tmp = *ptr;
                        *ptr = '\0';
                        if (tmp == '\0')
                        {
                                Store_InvalidateTag(store, buf, true);
                                return;
                        }
                        else
                        {
                                Store_InvalidateTag(store, buf, false);
                                *ptr = tmp;
                        }
                }
                else
                        ptr++;
        }
}

static interval_t
Store_TruncateIfAlreadyInvalidated(Store_t *store, interval_t interval,
                                   int nInvalTags,
                                   const char * const *invalTags)
{
        Assert(interval.stillValid);
        
        /*
         * Concrete upper bound is newer than last invalidation -- so
         * as far as we know it's still valid and we don't need to
         * check the buffer.
         */
        if (interval.upper > store->lastInvalTime)
                return interval;

        /*
         * Concrete upper bound is too old for the buffer -- we need
         * to assume it must have been invalidated by one of the
         * buffered invalidations we expired.
         */
        if (interval.upper <= store->bufferValidFrom)
        {
                interval.stillValid = false;
                return interval;
        }

        /* Actually need to check the buffer. */
        StoreInvalBuffer_Iterator_t *iter;
        StoreInvalBufferEntry_t query, *ent;
        
        query.pin = interval.upper;
        query.tag = NULL;
        query.time = 0;

        iter = StoreInvalBuffer_FindIter(&store->invalBuffer,
                                         query, StoreInvalBuffer_GE);
        if (!iter)
        {
                /* No buffered invalidations found; must still be valid */
                return interval;                
        }

        /*
         * Found some invalidations. Iterate through them and check
         * whether they match any of the supplied tags.
         */
        do
        {
                ent = StoreInvalBuffer_Iterator_Value(iter);

                /*
                 * XXX This is a n^2 check of every invalidation
                 * against every tag. Probably not the most efficient
                 * way.
                 */
                for (int i = 0; i < nInvalTags; i++)
                {
                        size_t tagLen = strlen(ent->tag);
                        if ((!ent->descendants &&
                             (strcmp(ent->tag, invalTags[i]) == 0)) ||
                            (ent->descendants &&
                             (strncmp(ent->tag, invalTags[i],
                                      tagLen) == 0)))
                        {
                                /* Invalidated! */
                                interval.stillValid = false;
                                interval.upper = ent->pin;
                                StoreInvalBuffer_Iterator_Release(iter);
                                return interval;
                        }
                }
        } while (StoreInvalBuffer_Iterator_Next(iter));

        StoreInvalBuffer_Iterator_Release(iter);

        /* No invalidations matched; still valid */
        return interval;
}

/*
 * Attempt to extend the interval for an existing entry. Returns true
 * if the new entry matched the old one and interval was extended;
 * false if the new entry still needs to be added.
 */
static bool
Store_TryExtendInterval(Store_t *store, entry_t *existing,
                        interval_t interval,
                        const void *key, size_t keyLen,
                        const void *data, size_t dataLen)
{
        if (!existing || !Interval_Overlaps(existing->interval, interval,
                                         store->lastInvalTime))
                return false;
        
        Debug(".. Overlaps " FMT_INTERVAL,
              VA_INTERVAL(existing->interval));

        
        // First check if we should extend it
        const char *warning = NULL;
        if (dataLen != existing->dataLen ||
            memcmp(data, existing->data, dataLen) != 0) {
                warning = "data disagrees";
        }
        if (existing->interval.lower > interval.lower) {
                warning = "lower bound is less";
        }
        if (existing->interval.lower < interval.lower) {
                Debug("Lower bound increasing from "
                      FMT_INTERVAL " to " FMT_INTERVAL,
                      VA_INTERVAL(existing->interval),
                      VA_INTERVAL(interval));
        }
        if (warning) {
                Warning("Attempting to extend interval of "
                        "existing entry for " FMT_VBLOB
                        " from " FMT_INTERVAL " to "
                        FMT_INTERVAL " but %s",
                        XVA_VBLOB(key, keyLen),
                        VA_INTERVAL(existing->interval),
                        VA_INTERVAL(interval), warning);
                Debug("Key");
                Message_Hexdump(key, keyLen);
                Debug("Current data");
                Message_Hexdump(existing->data, existing->dataLen);
                Debug("New data");
                Message_Hexdump(data, dataLen);

                // None of the warning cases should occur, and they
                // indicate a bug somewhere. However, that bug could
                // be an inadvertent nondeterminism in the application
                // code, so the best thing to do here is to proceed
                // with the insertion (after making the intervals
                // non-overlapping)
                return false;
        }

        
        if (existing->interval.upper > interval.upper) {
                // This can result from races between clients
                Notice("Ignoring request to decrease upper "
                       "bound from " FMT_INTERVAL
                       " to " FMT_INTERVAL,
                       VA_INTERVAL(existing->interval),
                       VA_INTERVAL(interval));
                // This isn't exactly a failure; do nothing
                return true;
        }
        if (existing->interval.upper == interval.upper) {
                // This can result from corner cases involving
                // running transactions in the present (for
                // example, a pin expires, but there haven't
                // actually been any writes)
                Notice("Ignoring repeat put for "
                       FMT_INTERVAL,
                       VA_INTERVAL(interval));
                // This isn't exactly a failure either
                return true;
        }

        // Everything checks out.  Extend the upper bound.
        existing->interval.upper = interval.upper;
                
        // Accounting
        MODSTAT(store, key, extensions, ++);

        // Update LRU
        store->evictionPolicy->put(store->evictionPolicyState,
                                   existing, true);
        return true;
}

/*
 * Actually add an entry to the cache, evicting items to make room,
 * and adding the new item to the invalidation and eviction indexes.
 *
 * Assumes that the appropriate version set exists and contains no
 * overlapping entries.
 *
 * Returns true on success and false on failure.
 */
static bool
Store_Add(Store_t *store, StoreVSet_t *vset,
          const void *key, size_t keyLen,
          interval_t interval,
          const void *data, size_t dataLen,
          int nInvalTags, const char * const *invalTags)
{
        // Do we need to evict?  Estimate the footprint we'll
        // have after we insert this item.  This doesn't quite
        // account for everything, but it's close.  The
        // current footprint that we compute is actually
        // pretty accurate, so we'll always ride slightly over
        // the desired footprint.
        size_t invalTagsLen;
        char **invalTagsCopy = Store_CopyInvalTags(nInvalTags,
                                                   invalTags,
                                                   &invalTagsLen);
        size_t curFootprint = Store_Footprint(store);
        size_t entryFootprint = Store_EntryFootprint(dataLen,
                                                     invalTagsLen);
        if (curFootprint + entryFootprint > store->maxBytes) {
                if (!Store_Evict(store,
                                 (entryFootprint -
                                  (store->maxBytes - curFootprint))))
                        return false;
        }

        // New entry
        entry_t *entry = StoreVSetTree_Insert(&vset->versions,
                                             interval.lower);
        if (!entry) {
                Warning("Failed to insert new cache entry");
                return false;
        }
        Assert(!entry->data);
        entry->interval = interval;                
        entry->data = malloc(dataLen);
        if (!entry->data) {
                Warning("Failed to allocate entry data");
                if (!StoreVSetTree_Remove(&vset->versions, interval.lower))
                        Panic("Failed to remove just-allocated entry");
                return false;
        }
        memcpy(entry->data, data, dataLen);
        entry->dataLen = dataLen;
        entry->vset = vset;
                
        entry->nInvalTags = nInvalTags;
        entry->invalTags = invalTagsCopy;
        entry->invalTagsLen = invalTagsLen;
        Store_RegisterInvalTags(store, entry);

        if (!interval.stillValid) {
                entry->timeInvalidated = time(NULL);
                EntryInvTimeOrder_PushFront(&store->invTimeOrder,
                                            entry);
        } else {
                entry->timeInvalidated = -1;
        }

        // Accounting
        store->entryBytes += entryFootprint;
        MODSTAT(store, key, entries, ++);
        entry->hitCount = 0;

        // Update LRU
        store->evictionPolicy->put(store->evictionPolicyState,
                                   entry, false);

        Store_AssertSize(store);
        return true;
}

bool
Store_Put(Store_t *store, const void *key, size_t keyLen, interval_t interval,
          const void *data, size_t dataLen, int nInvalTags,
          const char * const *invalTags, bool force)
{
        Debug("Putting data " FMT_BLOB " with key " FMT_VBLOB " at "
              FMT_INTERVAL, VA_BLOB(data, dataLen), XVA_VBLOB(key, keyLen),
              VA_INTERVAL(interval));
        
        // Must have invalidation tags if still valid
        if (interval.stillValid && (nInvalTags == 0))
                return false;

        if (interval.stillValid)
        {
                Debug("Inserting still-valid entry with interval "
                       FMT_INTERVAL " nTags %d  tag0 %s",
                       VA_INTERVAL(interval), nInvalTags, invalTags[0]);
                interval = Store_TruncateIfAlreadyInvalidated(store,
                                                              interval,
                                                              nInvalTags,
                                                              invalTags);
                if (!interval.stillValid)
                        Debug("Item was truncated by buffered inval");
        }

        // Invalidation tags are useless if not still valid
        if (!interval.stillValid)
                nInvalTags = 0;

        // Eagerly remove useless items
        Store_RemoveUseless(store);
        
        StoreVSet_t *vset = StoreHT_Get(&store->lines, key, keyLen);
        if (!vset) {
                Debug(".. New key");
                vset = malloc(sizeof *vset);
                if (!vset) {
                        Warning("Failed to allocate new version set");
                        return false;
                }
                char *keyCopy = malloc(keyLen);
                if (!keyCopy) {
                        Warning("Failed to allocate key copy");
                        free(vset);
                        return false;
                }
                memcpy(keyCopy, key, keyLen);
                StoreVSetTree_Init(&vset->versions);
                StoreHT_Insert(&store->lines, vset, keyCopy, keyLen);

                // Accounting
                store->keyBytes += MALLOC_SIZE(keyLen);
        }

        entry_t *entry = StoreVSetTree_Find(&vset->versions,
                                            interval.upper,
                                            StoreVSetTree_LT);
        
        // If force is set, just blow the old entry away
        if (entry && force)
        {
                Store_RemoveEntry(store, entry);
                entry = NULL;
        }
        
        if (entry && entry->interval.stillValid &&
            (interval.lower >= store->lastInvalTime))
        {
                // We must have missed an invalidation.
                //
                // Treat this as an implicit invalidation of the
                // existing entry at lastInvalTime, then recheck
                // whether it overlaps (if it does, this *is* probably
                // an error).
                Store_TruncateUnboundedInterval(store, entry,
                                                store->lastInvalTime);   
        }
        

        // See if we need to extend the existing interval
        if (Store_TryExtendInterval(store, entry, interval,
                                    key, keyLen, data, dataLen)) {
                return true;
        }

        // Make sure that our interval doesn't overlap with the
        // existing one.
        if (entry && (entry->interval.upper > interval.lower)) {
                interval.lower = entry->interval.upper;
                if (interval.lower >= interval.upper) {
                        return false;
                }
        }
        
#if 1
        // XXX Check if this addition might be an extension
        if (entry && dataLen == entry->dataLen &&
            memcmp(data, entry->data, dataLen) == 0) {
                Debug("Possible extension of " FMT_INTERVAL,
                      VA_INTERVAL(entry->interval));
                MODSTAT(store, key, potentialExtensions, ++);
        }
#endif

        return Store_Add(store, vset, key, keyLen, interval,
                         data, dataLen, nInvalTags, invalTags);
}

#define FMT_VAL_PCT "%ld (%.2g%%)"
#define VA_VAL_PCT(v, of) (v), (of) > 0 ? (double)((v) * 100) / (of) : 0

static void
Store_DumpClassStats(char *buf, const char *name, StoreClassStats_t *stats)
{
        sprintf(buf, "%-18s %7ld %5ld (%5.1f%%) %5ld %5ld %5ld %5ld %5ld %5ld %5ld\n", name,
                stats->lookups, VA_VAL_PCT(stats->hits, stats->lookups),
                stats->keyMisses, stats->stalenessMisses,
                stats->capacityMisses, stats->consistencyMisses,
                stats->extensions, stats->potentialExtensions,
                stats->entries);
}

char *
Store_DumpStats(Store_t *store)
{
        // XXX This is terrible
        char *buf = malloc(80*100);
        char *pos = buf;
        StoreClassStatsHash_Iter_t it;
        StoreClassStats_t *stats;

        StoreClassStatsHash_Iter(&store->classStats, &it);

        sprintf(pos, "%18s %7s %14s %5s %5s %5s %5s %5s %5s  %5s\n", "",
                "lookups", "hits", "keyMi", "stlMi", "capMi", "conMi", "extns", "pExts", "entrs");
        pos += strlen(pos);
        while ((stats = StoreClassStatsHash_IterNext(&it))) {
                Store_DumpClassStats(pos, (char*)stats->link.key, stats);
                pos += strlen(pos);
        }
        Store_DumpClassStats(pos, "Total", &store->totalStats);
        pos += strlen(pos);

#define PR_LU(f) sprintf(pos, #f ": %lu\n", (long unsigned)store->f); pos += strlen(pos)
#define PR_VAL_PCT(f, of) \
        sprintf(pos, #f ": " FMT_VAL_PCT "\n", VA_VAL_PCT(store->f, store->of)); \
        pos += strlen(pos)

        size_t htBytes;
        int keys;
        keys = StoreHT_Size(&store->lines, &htBytes);
        sprintf(pos, "keys: %d\n", keys);
        pos += strlen(pos);

        sprintf(pos, "htBytes: %lu\n", (long unsigned)htBytes);
        pos += strlen(pos);
        PR_LU(keyBytes);
        PR_LU(entryBytes);

        return buf;
}

void
Store_PrintStats(Store_t *store)
{
        char *buf = Store_DumpStats(store);
        printf("%s\n", buf);
        free(buf);
        fflush(stdout);
}

void
Store_ClearStats(Store_t *store)
{
        StoreClassStatsHash_Iter_t it;
        StoreClassStats_t *stats;
        
        StoreClassStatsHash_Iter(&store->classStats, &it);

        while ((stats = StoreClassStatsHash_IterNext(&it))) {
                StoreClassStatsHash_Unlink(&store->classStats, stats);
        }
        memset(&store->totalStats, 0, sizeof store->totalStats);
}

static void
Store_ExpireBuffer(Store_t *store)
{
        StoreInvalBuffer_Iterator_t *iter;
        StoreInvalBufferEntry_t *ent;
        time_t curTime = time(NULL);

        while (StoreInvalBuffer_Size(&store->invalBuffer) > 0)
        {
                iter = StoreInvalBuffer_First(&store->invalBuffer);
                Assert(iter != NULL);
                ent = StoreInvalBuffer_Iterator_Value(iter);
                StoreInvalBuffer_Iterator_Release(iter);

                if ((ent->time + INVAL_BUFFER_TIME) > curTime)
                        break;

                store->bufferValidFrom = ent->pin;
                StoreInvalBuffer_Remove(&store->invalBuffer, *ent);
        }
}

void
Store_Invalidate(Store_t *store, pin_t ts, int nInvalTags,
                 const char * const *invalTags)
{
        Assert(!PIN_SPECIAL(ts));
        Assert(ts > store->lastInvalTime);

        Debug("Incrementing lastInvalTime from " FMT_PIN " to " FMT_PIN,
              VA_PIN(store->lastInvalTime), VA_PIN(ts));
        
        store->lastInvalTime = ts;
        
        if (nInvalTags > 0)
        {
                for (int i = 0; i < nInvalTags; i++)
                {
                        /* Invalidate tags */
                        Store_InvalidateTagAndAncestorsDescendants(store,
                                                                   invalTags[i]);
                }
        }

        Store_ExpireBuffer(store);
}

void
Store_RemoveUseless(Store_t *store)
{
        time_t removeOlderThan = time(NULL) - store->uselessBound;

        while ((store->invTimeOrder.tail != NULL) &&
               (store->invTimeOrder.tail->timeInvalidated < removeOlderThan))
        {
                Store_RemoveEntry(store, store->invTimeOrder.tail);
        }
}

void
Store_EvictionOrderIterReset(Store_t *store)
{
        store->evictionPolicy->iterReset(store->evictionPolicyState);
}

entry_t *
Store_EvictionOrderIterNext(Store_t *store)
{
        return store->evictionPolicy->iterNext(store->evictionPolicyState);
}
