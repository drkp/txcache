/*-------------------------------------------------------------------------
 *
 * ssnap.c
 *   Saving and restoring snapshots; multiversion support for
 *   transactional caching
 *
 * IDENTIFICATION
 *	  $PostgreSQL$
 *
 *-------------------------------------------------------------------------
 */

#include "postgres.h"

#include "utils/ssnap.h"
#include "utils/dynahash.h"
#include "utils/memutils.h"
#include "utils/hsearch.h"
#include "storage/lwlock.h"
#include "storage/shmem.h"
#include "utils/tqual.h"
#include "miscadmin.h"
#include "utils/xstamp.h"
#include "commands/inval.h"

#define REPORT_TRANSACTION_XSTAMPS 0

#define MAX_SAVED_SNAPSHOTS 1024
#define MAX_CONNECTIONS 500

typedef struct SSNAP
{
	/* hash key */
	XStamp    tag;

	/* data */
	SnapshotData snapshot;
	TransactionId xip[MAX_CONNECTIONS];
	TransactionId subxip[MAX_CONNECTIONS];
} SSNAP;

typedef struct SSnapSharedData
{
	XStamp NextXStamp;
	XStamp LastSSnapXStamp;
	XStamp EarliestSSnapXStamp;
} SSnapSharedData;

static HTAB *SSnapHash;
static CommitMap *TheCommitMap;
static LWLockId SSnapHashLock;
static SSnapSharedData *SSnapShared;
static XStamp MyLastCommittedXStamp;

void
SSnapInit()
{
	HASHCTL info;
	int hash_flags;
	bool found;

	Assert(MaxBackends <= MAX_CONNECTIONS);

	MemSet(&info, 0, sizeof(info));
	info.keysize = sizeof(XStamp);
	info.entrysize = sizeof(SSNAP);
	info.hash = tag_hash;
	info.num_partitions = NUM_LOCK_PARTITIONS;
	hash_flags = (HASH_ELEM | HASH_FUNCTION);

	SSnapHash = ShmemInitHash("SSnap Hash",
							  MAX_SAVED_SNAPSHOTS / 2,
							  MAX_SAVED_SNAPSHOTS,
							  &info,
							  hash_flags);


	if (!SSnapHash) {
		elog(FATAL, "could not initialize saved snapshot hash table");
	}

	TheCommitMap = CommitMapShmemAlloc();

	SSnapShared =
		ShmemInitStruct("SSnapSharedData", sizeof(SSnapSharedData),
						&found);
	if (!SSnapShared) {
		elog(FATAL, "could not initialize ssnap shared memory");
	}	

	if (!found) {
		SSnapShared->NextXStamp = FirstXStamp;
		SSnapShared->LastSSnapXStamp = InvalidXStamp;
		SSnapShared->EarliestSSnapXStamp = InvalidXStamp;
	}
	SSnapHashLock = LWLockAssign();
}

Size
SSnapShmemSize()
{
	return (sizeof(SSNAP) * MAX_SAVED_SNAPSHOTS +
			sizeof(SSnapSharedData) +
			CommitMapShmemSize());
}

/*
 * Save a snapshot, recording it in the saved snapshot table, and
 * return an id that can be used to reference it.
 */
XStamp
SaveSnapshot(struct timeval *tp)
{
	MemoryContext oldcontext;
	SSNAP *ssnap;
	XStamp ssnapId;
	Snapshot snapshot;
	int res;
	
	LWLockAcquire(SSnapHashLock, LW_EXCLUSIVE);

	/* the transaction must not have already snapshotted */
	if (TransactionHasSerializableSnapshot()) {
		ereport(ERROR,
				(errcode(ERRCODE_INVALID_TRANSACTION_STATE),
				 errmsg("transaction already snapshotted before pin")));
		return InvalidXStamp;
	}

	/* return an up-to-date time stamp even if this is already saved */
	if (tp != NULL)
	{
		res = gettimeofday(tp, NULL);
		Assert(res == 0);
	}

	/* snapshot the transaction */
	snapshot = GetTransactionSnapshot();
	
	ssnapId = SSnapShared->NextXStamp;

	if (ssnapId == SSnapShared->LastSSnapXStamp) {
		/* already saved; return the old XStamp */
		LWLockRelease(SSnapHashLock);
		return ssnapId;
	}

	SSnapShared->LastSSnapXStamp = ssnapId;
	if (!XSTAMP_IS_VALID(SSnapShared->EarliestSSnapXStamp))
	{
		SSnapShared->EarliestSSnapXStamp = ssnapId;
	}
	
	ssnap = hash_search(SSnapHash,
						&ssnapId,
						HASH_ENTER,
						NULL);
	Assert(ssnap != NULL);
	
	ssnap->snapshot.xmin = snapshot->xmin;
	ssnap->snapshot.xmax = snapshot->xmax;
	ssnap->snapshot.curcid = snapshot->curcid;
	ssnap->snapshot.xcnt = snapshot->xcnt;
	ssnap->snapshot.subxcnt = snapshot->subxcnt;
	memcpy(ssnap->xip, snapshot->xip,
		   sizeof(TransactionId) * snapshot->xcnt);
	memcpy(ssnap->subxip, snapshot->subxip,
		   sizeof(TransactionId) * snapshot->subxcnt);
	
	LWLockRelease(SSnapHashLock);
	
	return ssnapId;
}

Snapshot
GetSavedSnapshot(XStamp id)
{
	SSNAP *ssnap;
	Snapshot snap = NULL;
	
	LWLockAcquire(SSnapHashLock, LW_SHARED);
	
	ssnap = hash_search(SSnapHash,
						&id,
						HASH_FIND,
						NULL);

	if (ssnap != NULL) {
		ssnap->snapshot.xip = ssnap->xip;
		ssnap->snapshot.subxip = ssnap->subxip;
		snap = CopySnapshot(&ssnap->snapshot);
	}

	LWLockRelease(SSnapHashLock);
	
	return snap;
}

bool
RemoveSavedSnapshot(XStamp id)
{
	SSNAP *ssnap;
	bool found = false;
	HASH_SEQ_STATUS status;

	LWLockAcquire(SSnapHashLock, LW_EXCLUSIVE);

	ssnap = hash_search(SSnapHash,
						&id,
						HASH_FIND,
						NULL);

	if (ssnap != NULL) {
		hash_search(SSnapHash,
					&id,
					HASH_REMOVE,
					NULL);
		found = true;
	}

	if (SSnapShared->LastSSnapXStamp == id)
	{
		SSnapShared->LastSSnapXStamp = InvalidXStamp;
	}

	if (SSnapShared->EarliestSSnapXStamp == id)
	{
		SSnapShared->EarliestSSnapXStamp = XStampInfinity;
		
		hash_seq_init(&status, SSnapHash);

		while ((ssnap = hash_seq_search(&status)) != NULL)
		{
			if (ssnap->tag < SSnapShared->EarliestSSnapXStamp)
			{
				SSnapShared->EarliestSSnapXStamp = ssnap->tag;
			}
		}

		CommitMapExpire(TheCommitMap,
						SSnapShared->EarliestSSnapXStamp);
		
		if (SSnapShared->EarliestSSnapXStamp == XStampInfinity)
		{
			SSnapShared->EarliestSSnapXStamp = InvalidXStamp;
		}
	}

	LWLockRelease(SSnapHashLock);

	return found;
}

/*
 * Check whether a transaction is invisible to some saved
 * snapshot. For a vacuum satisfiability check, if the Xmax is
 * invisible to a saved snapshot, we can't vacuum it
 * yet. Conveniently, with aggressive garbage-collection of the commit
 * map, this is a very easy thing to do: if the xid is in the commit
 * map, there must be a saved snapshot before it, and thus that
 * transaction is invisible to that snapshot.
 */
bool
XidIsInvisibleToSomeSavedSnapshot(TransactionId xid)
{
	return XSTAMP_IS_VALID(CommitMapLookup(TheCommitMap, xid));
}

void
PrepareCommitXStamp()
{
	LWLockAcquire(SSnapHashLock, LW_EXCLUSIVE);
}

XStamp
GetCommitXStamp(TransactionId xid, bool invalidate)
{
	XStamp xstamp;
	
	xstamp = SSnapShared->NextXStamp;
	SSnapShared->NextXStamp++;

#if REPORT_TRANSACTION_XSTAMPS
	ereport(WARNING,
			(errcode(ERRCODE_WARNING),
			 errmsg("assigning transaction xstamp: %d -> %d", xid, xstamp)));
#endif

	/*
	 * We don't need to add it to the commit map if there are no
	 * active saved snapshots, because then no saved snapshot can be
	 * earlier than this transaction.
	 */
	if (XSTAMP_IS_VALID(SSnapShared->EarliestSSnapXStamp))
	{
		CommitMapAdd(TheCommitMap, xid, xstamp);		
	}


	if (invalidate)
		AtCommit_Invalidate(xstamp);
	
	LWLockRelease(SSnapHashLock);

	MyLastCommittedXStamp = xstamp;
	return xstamp;
}

XStamp
XidToXStamp(TransactionId xid)
{
	return CommitMapLookup(TheCommitMap, xid);
}

XStamp
GetNextXStamp()
{
	return SSnapShared->NextXStamp;
}

XStamp
GetEarliestSavedSnapshotID()
{
	return SSnapShared->EarliestSSnapXStamp;
}
