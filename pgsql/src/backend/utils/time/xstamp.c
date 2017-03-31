/*-------------------------------------------------------------------------
 *
 * xstamp.h
 *   Multiversion support for transactional caching: XStamps. XStamps
 *   are transaction timestamps in a commit ordering, as distinguished
 *   from xids, which are not ordered. A map is stored from xid to
 *   XStamp.
 *
 * IDENTIFICATION
 *	  $PostgreSQL$
 *
 *-------------------------------------------------------------------------
 */
#include "postgres.h"

#include "utils/memutils.h"
#include "utils/hsearch.h"
#include "storage/lwlock.h"
#include "storage/shmem.h"
#include "utils/tqual.h"
#include "miscadmin.h"
#include "storage/spin.h"

#define MAX_COMMIT_MAP_ENTRIES 262144
#define COMMIT_MAP_BUCKETS 8192

typedef struct CommitMapEntry CommitMapEntry;

struct CommitMapEntry
{
	TransactionId xid;
	XStamp xstamp;

	CommitMapEntry *bucketPrev, *bucketNext, *expirePrev, *expireNext;
};

typedef struct CommitMapBucket
{
	CommitMapEntry *head, *tail;
	slock_t lock;
} CommitMapBucket;

struct CommitMap
{
	CommitMapEntry entries[MAX_COMMIT_MAP_ENTRIES];
	CommitMapBucket buckets[COMMIT_MAP_BUCKETS];
	CommitMapEntry *expireHead, *expireTail;
	CommitMapEntry *freeHead;
	slock_t expireChainLock;
};

static int
HashXid(TransactionId xid)
{
	return (xid % COMMIT_MAP_BUCKETS);
}

static CommitMapEntry *
CommitMapEntryAlloc(CommitMap *map)
{
	CommitMapEntry *entry;
	
	/* must hold expire chain lock */
	Assert(!SpinLockFree(&map->expireChainLock));

	if (map->freeHead == NULL)
	{
		ereport(PANIC, (errmsg_internal("xid->xstamp commit map full!"
										"earliest xstamp: %ld",
										map->expireHead->xstamp)));
	}
	Assert(map->freeHead != NULL);

	entry = map->freeHead;
	map->freeHead = entry->expireNext;

	return entry;
}

static void
CommitMapEntryFree(CommitMap *map, CommitMapEntry *entry)
{
	entry->expireNext = map->freeHead;
	map->freeHead = entry;
}

static void
CommitMapInit(CommitMap *map)
{
	int i;

	for (i = 0; i < COMMIT_MAP_BUCKETS; i++)
	{
		map->buckets[i].head = NULL;
		map->buckets[i].tail = NULL;
		SpinLockInit(&map->buckets[i].lock);
	}

	map->expireHead = NULL;
	map->expireTail = NULL;

	/* initialize free list */
	map->freeHead = NULL;

	for (i = 0; i < MAX_COMMIT_MAP_ENTRIES; i++)
	{
		CommitMapEntryFree(map, &map->entries[i]);
	}

	SpinLockInit(&map->expireChainLock);
}

static void
CommitMapAddEntry(CommitMap *map, CommitMapEntry *entry)
{
	CommitMapBucket *bucket;
	int hash;
	
	/* must hold expire chain lock */
	Assert(!SpinLockFree(&map->expireChainLock));
	
	/* expire chain must be ordered */
	Assert((map->expireTail == NULL) ||
		   (entry->xstamp > map->expireTail->xstamp));

	/* add to expire chain */
	entry->expirePrev = map->expireTail;
	entry->expireNext = NULL;
	if (map->expireTail != NULL)
	{
		map->expireTail->expireNext = entry;		
	}
	map->expireTail = entry;
	if (map->expireHead == NULL)
	{
		map->expireHead = entry;
	}

	/* find bucket */
	hash = HashXid(entry->xid);
	bucket = &map->buckets[hash];

	/* add to bucket */
	SpinLockAcquire(&bucket->lock);
	entry->bucketPrev = bucket->tail;
	entry->bucketNext = NULL;
	if (bucket->tail != NULL)
	{
		bucket->tail->bucketNext = entry;
	}
	bucket->tail = entry;
	if (bucket->head == NULL)
	{
		bucket->head = entry;
	}
	SpinLockRelease(&bucket->lock);
}

static void
CommitMapRemove(CommitMap *map, CommitMapEntry *entry)
{
	CommitMapBucket *bucket;
	int hash;
	
	/* must hold expire chain lock */
	Assert(!SpinLockFree(&map->expireChainLock));

	/*
	 * don't bother removing it from the expire chain; we assume we
	 * just truncate the chain later
	 */

	/* find & lock bucket */
	hash = HashXid(entry->xid);
	bucket = &map->buckets[hash];
	SpinLockAcquire(&bucket->lock);

	if (entry->bucketNext != NULL)
	{
		entry->bucketNext->bucketPrev = entry->bucketPrev;
	}
	else
	{
		bucket->tail = entry->bucketPrev;
	}

	if (entry->bucketPrev != NULL)
	{
		entry->bucketPrev->bucketNext = entry->bucketNext;
	}
	else
	{
		bucket->head = entry->bucketNext;
	}
	SpinLockRelease(&bucket->lock);

	/* return entry to free list */
	CommitMapEntryFree(map, entry);
}

XStamp
CommitMapLookup(CommitMap *map, TransactionId xid)
{
	CommitMapEntry *ptr;
	XStamp xstamp;
	int hash;
	CommitMapBucket *bucket;
	
	/* find & lock bucket */
	hash = HashXid(xid);
	bucket = &map->buckets[hash];
	SpinLockAcquire(&bucket->lock);

	ptr = bucket->head;
	
	while (ptr != NULL)
	{
		if (ptr->xid == xid)
		{
			xstamp = ptr->xstamp;
			SpinLockRelease(&bucket->lock);
#ifdef DEBUG_REPORT_XSTAMPS
			ereport(WARNING,
					(errcode(ERRCODE_WARNING),
					 (errmsg_internal("commit map lookup for %ld: "
									  "xstamp %ld",
									  xid, xstamp))));
#endif /* DEBUG_REPORT_XSTAMPS */
			return xstamp;
		}

		ptr = ptr->bucketNext;
	}

	SpinLockRelease(&bucket->lock);
	
#ifdef DEBUG_REPORT_XSTAMPS
	ereport(WARNING,
			(errcode(ERRCODE_WARNING),
			 (errmsg_internal("commit map lookup for %ld: "
							  "not found"))));
#endif /* DEBUG_REPORT_XSTAMPS */
	
	return InvalidXStamp;
}

void
CommitMapAdd(CommitMap *map, TransactionId xid, XStamp xstamp)
{
	CommitMapEntry *entry;
	
	/* acquire expire chain lock */
	SpinLockAcquire(&map->expireChainLock);

	/* allocate entry */
	entry = CommitMapEntryAlloc(map);

	/* fill it in */
	entry->xid = xid;
	entry->xstamp = xstamp;

	/* insert it into the map */
	CommitMapAddEntry(map, entry);

	/* release expire chain lock */
	SpinLockRelease(&map->expireChainLock);

#ifdef DEBUG_REPORT_XSTAMPS
	ereport(WARNING,
			(errcode(ERRCODE_WARNING),
			 (errmsg_internal("adding to commit map: "
							  "xid %ld -> xstamp %ld",
							  xid, xstamp))));
#endif /* DEBUG_REPORT_XSTAMPS */
}

void
CommitMapExpire(CommitMap *map, XStamp earliestToKeep)
{
	CommitMapEntry *entry, *oldEntry;

#ifdef DEBUG_REPORT_XSTAMPS
	ereport(WARNING,
			(errcode(ERRCODE_WARNING),
			 (errmsg_internal("expiring from commit map: "
							  "older than xstamp %ld",
							  earliestToKeep))));
#endif /* DEBUG_REPORT_XSTAMPS */

	/* acquire expire chain lock */
	SpinLockAcquire(&map->expireChainLock);

	/* remove elements until we find one to keep */
	entry = map->expireHead;
	while ((entry != NULL) &&
		   (entry->xstamp < earliestToKeep))
	{
		oldEntry = entry;
		entry = oldEntry->expireNext;
		CommitMapRemove(map, oldEntry);
	}

	/* update the head pointer to point to the first element kept */
	map->expireHead = entry;
	if (entry == NULL)
	{
		map->expireTail = NULL;
	}
	
	/* release expire chain lock */
	SpinLockRelease(&map->expireChainLock);
}

Size
CommitMapShmemSize()
{
	return sizeof(CommitMap);
}

CommitMap *
CommitMapShmemAlloc()
{
	CommitMap *map;
	bool found;

	map = ShmemInitStruct("CommitMap", sizeof(CommitMap),
						  &found);

	if (!found)
	{
		CommitMapInit(map);
	}

	return map;
}
