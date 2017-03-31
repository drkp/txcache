// -*- c-file-style: "bsd" -*-

#include "store.h"
#include "assert.h"

#include "lib/iobuf.h"
#include "lib/iobuf-getput.h"

static void Store_PutEntry(entry_t *entry, IOBuf_t *buf, bool full);

void
Store_Dump(Store_t *store, int fd)
{
        IOBuf_t buf;
        entry_t *entry;

        IOBuf_Init(&buf);

        // Walk through the store contents in eviction-order so when
        // we restore the LRU lists should match
        Store_EvictionOrderIterReset(store);
        while ((entry = Store_EvictionOrderIterNext(store)) != NULL)
        {
                if (!entry->interval.stillValid)
                        continue;
                IOBuf_PutInt8(&buf, 1);
                Store_PutEntry(entry, &buf, false);                
                IOBuf_WriteFile(&buf, fd);
        }
        IOBuf_PutInt8(&buf, 0);
        IOBuf_WriteFile(&buf, fd);
        
        IOBuf_Release(&buf);
}

static void
Store_PutEntry(entry_t *entry, IOBuf_t *buf, bool full)
{
        IOBuf_PutBuf(buf, entry->vset->linesLink.key,
                     entry->vset->linesLink.keyLen);
        IOBuf_PutBuf(buf, entry->data, entry->dataLen);
        IOBuf_PutInt32(buf, entry->nInvalTags);
        for (int i = 0; i < entry->nInvalTags; ++i)
                IOBuf_PutString(buf, entry->invalTags[i]);
        if (full) {
                IOBuf_PutInt32(buf, entry->interval.lower);
                IOBuf_PutInt32(buf, entry->interval.upper);
                IOBuf_PutInt8(buf, entry->interval.stillValid);
                IOBuf_PutInt32(buf, entry->timeInvalidated);
                IOBuf_PutInt32(buf, entry->hitCount);                
        }
}

void
Store_DebugDump(Store_t *store, int fd)
{
        IOBuf_t buf;
        StoreHT_Iter_t htiter;
        StoreVSet_t *vset;
        StoreVSetTree_Iterator_t *vsetiter;
        
        IOBuf_Init(&buf);

        StoreHT_Iter(&store->lines, &htiter);

        while ((vset = StoreHT_IterNext(&htiter)) != NULL) {
                IOBuf_PutInt8(&buf, 1);
                IOBuf_PutBuf(&buf, vset->linesLink.key,
                             vset->linesLink.keyLen);
                int n = StoreVSetTree_Size(&vset->versions);
                int i = 0;
                IOBuf_PutInt32(&buf, n);
                if (n > 0) {
                        vsetiter = StoreVSetTree_First(&vset->versions);
                        do
                        {
                                entry_t *entry =
                                        StoreVSetTree_Iterator_Value(vsetiter);
                                Store_PutEntry(entry, &buf, true);
                                i++;
                        } while (StoreVSetTree_Iterator_Next(vsetiter));
                        StoreVSetTree_Iterator_Release(vsetiter);
                        assert(i == n);
                }
                IOBuf_WriteFile(&buf, fd);
        }
        
        IOBuf_Release(&buf);
}

/**
 * store must be an initialized but empty store.
 */
void
Store_Load(Store_t *store, int fd)
{
        
        Assert(store->keyBytes == 0);
        Assert(store->entryBytes == 0);

        interval_t unbounded = {
                .lower = PIN_NEG_INF,
                .upper = PIN_MIN,
                .stillValid = true,
        };

        IOBuf_t buf;
        IOBuf_InitFile(&buf, fd);
        
        const char **invalTags = NULL;
        int maxInvalTags = 0;
        while (IOBuf_GetInt8(&buf)) {
                const void *key, *data;
                size_t keyLen, dataLen;
                int nInvalTags;
                key = IOBuf_GetBuf(&buf, &keyLen);
                data = IOBuf_GetBuf(&buf, &dataLen);
                nInvalTags = IOBuf_GetInt32(&buf);
                if (nInvalTags > maxInvalTags) {
                        invalTags = realloc(invalTags, nInvalTags * sizeof invalTags[0]);
                        if (!invalTags)
                                Panic("Failed to allocate %d invalTags", nInvalTags);
                        maxInvalTags = nInvalTags;
                }
                for (int i = 0; i < nInvalTags; ++i)
                        invalTags[i] = IOBuf_GetString(&buf);

                bool success = Store_Put(store, key, keyLen, unbounded,
                                         data, dataLen, nInvalTags, invalTags,
                                         false);
                Assert(success);
        }

        if (invalTags)
                free(invalTags);

        Notice("Snapshot loaded. Store contains %ld entries (%zd bytes)",
               store->totalStats.entries, Store_Footprint(store));
        
        IOBuf_Release(&buf);
}
