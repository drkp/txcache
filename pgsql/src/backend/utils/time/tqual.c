/*-------------------------------------------------------------------------
 *
 * tqual.c
 *	  POSTGRES "time" qualification code, ie, tuple visibility rules.
 *
 * The caller must hold at least a shared buffer context lock on the buffer
 * containing the tuple.  (VACUUM FULL assumes it's sufficient to have
 * exclusive lock on the containing relation, instead.)
 *
 * NOTE: all the HeapTupleSatisfies routines will update the tuple's
 * "hint" status bits if we see that the inserting or deleting transaction
 * has now committed or aborted.
 *
 * NOTE: must check TransactionIdIsInProgress (which looks in PGPROC array)
 * before TransactionIdDidCommit/TransactionIdDidAbort (which look in
 * pg_clog).  Otherwise we have a race condition: we might decide that a
 * just-committed transaction crashed, because none of the tests succeed.
 * xact.c is careful to record commit/abort in pg_clog before it unsets
 * MyProc->xid in PGPROC array.  That fixes that problem, but it also
 * means there is a window where TransactionIdIsInProgress and
 * TransactionIdDidCommit will both return true.  If we check only
 * TransactionIdDidCommit, we could consider a tuple committed when a
 * later GetSnapshotData call will still think the originating transaction
 * is in progress, which leads to application-level inconsistency.	The
 * upshot is that we gotta check TransactionIdIsInProgress first in all
 * code paths, except for a few cases where we are looking at
 * subtransactions of our own main transaction and so there can't be any
 * race condition.
 *
 *
 * Portions Copyright (c) 1996-2006, PostgreSQL Global Development Group
 * Portions Copyright (c) 1994, Regents of the University of California
 *
 * IDENTIFICATION
 *	  $PostgreSQL: pgsql/src/backend/utils/time/tqual.c,v 1.100 2006/11/17 18:00:15 tgl Exp $
 *
 *-------------------------------------------------------------------------
 */

#include "postgres.h"

#include "access/multixact.h"
#include "access/subtrans.h"
#include "access/transam.h"
#include "access/xact.h"
#include "storage/bufmgr.h"
#include "storage/procarray.h"
#include "utils/tqual.h"
#include "utils/ssnap.h"

/*
 * These SnapshotData structs are static to simplify memory allocation
 * (see the hack in GetSnapshotData to avoid repeated malloc/free).
 */
static SnapshotData SnapshotDirtyData;
static SnapshotData SerializableSnapshotData;
static SnapshotData LatestSnapshotData;

/* Externally visible pointers to valid snapshots: */
Snapshot	SnapshotDirty = &SnapshotDirtyData;
Snapshot	SerializableSnapshot = NULL;
Snapshot	LatestSnapshot = NULL;

/*
 * This pointer is not maintained by this module, but it's convenient
 * to declare it here anyway.  Callers typically assign a copy of
 * GetTransactionSnapshot's result to ActiveSnapshot.
 */
Snapshot	ActiveSnapshot = NULL;

/* These are updated by GetSnapshotData: */
TransactionId TransactionXmin = InvalidTransactionId;
TransactionId RecentXmin = InvalidTransactionId;
TransactionId RecentGlobalXmin = InvalidTransactionId;

HTSS_Reason LastHTSSReason;

/*
 * HeapTupleSatisfiesItself
 *		True iff heap tuple is valid "for itself".
 *
 *	Here, we consider the effects of:
 *		all committed transactions (as of the current instant)
 *		previous commands of this transaction
 *		changes made by the current command
 *
 * Note:
 *		Assumes heap tuple is valid.
 *
 * The satisfaction of "itself" requires the following:
 *
 * ((Xmin == my-transaction &&				the row was updated by the current transaction, and
 *		(Xmax is null						it was not deleted
 *		 [|| Xmax != my-transaction)])			[or it was deleted by another transaction]
 * ||
 *
 * (Xmin is committed &&					the row was modified by a committed transaction, and
 *		(Xmax is null ||					the row has not been deleted, or
 *			(Xmax != my-transaction &&			the row was deleted by another transaction
 *			 Xmax is not committed)))			that has not been committed
 */
bool
HeapTupleSatisfiesItself(HeapTupleHeader tuple, Buffer buffer)
{
	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
			return false;

		if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return false;
			if (!TransactionIdIsInProgress(xvac))
			{
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (!TransactionIdIsCurrentTransactionId(xvac))
			{
				if (TransactionIdIsInProgress(xvac))
					return false;
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_COMMITTED;
					SetBufferCommitInfoNeedsSave(buffer);
				}
				else
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
			}
		}
		else if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmin(tuple)))
		{
			if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid */
				return true;

			if (tuple->t_infomask & HEAP_IS_LOCKED)		/* not deleter */
				return true;

			Assert(!(tuple->t_infomask & HEAP_XMAX_IS_MULTI));

			/* deleting subtransaction aborted? */
			if (TransactionIdDidAbort(HeapTupleHeaderGetXmax(tuple)))
			{
				tuple->t_infomask |= HEAP_XMAX_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return true;
			}

			Assert(TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)));

			return false;
		}
		else if (TransactionIdIsInProgress(HeapTupleHeaderGetXmin(tuple)))
			return false;
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmin(tuple)))
		{
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/* it must have aborted or crashed */
			tuple->t_infomask |= HEAP_XMIN_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			return false;
		}
	}

	/* by here, the inserting transaction has committed */

	if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid or aborted */
		return true;

	if (tuple->t_infomask & HEAP_XMAX_COMMITTED)
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return true;
		return false;			/* updated by other */
	}

	if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
	{
		/* MultiXacts are currently only allowed to lock tuples */
		Assert(tuple->t_infomask & HEAP_IS_LOCKED);
		return true;
	}

	if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)))
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return true;
		return false;
	}

	if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
		return true;

	if (!TransactionIdDidCommit(HeapTupleHeaderGetXmax(tuple)))
	{
		/* it must have aborted or crashed */
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return true;
	}

	/* xmax transaction committed */

	if (tuple->t_infomask & HEAP_IS_LOCKED)
	{
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return true;
	}

	tuple->t_infomask |= HEAP_XMAX_COMMITTED;
	SetBufferCommitInfoNeedsSave(buffer);
	return false;
}

/*
 * HeapTupleSatisfiesNow
 *		True iff heap tuple is valid "now".
 *
 *	Here, we consider the effects of:
 *		all committed transactions (as of the current instant)
 *		previous commands of this transaction
 *
 * Note we do _not_ include changes made by the current command.  This
 * solves the "Halloween problem" wherein an UPDATE might try to re-update
 * its own output tuples.
 *
 * Note:
 *		Assumes heap tuple is valid.
 *
 * The satisfaction of "now" requires the following:
 *
 * ((Xmin == my-transaction &&				inserted by the current transaction
 *	 Cmin < my-command &&					before this command, and
 *	 (Xmax is null ||						the row has not been deleted, or
 *	  (Xmax == my-transaction &&			it was deleted by the current transaction
 *	   Cmax >= my-command)))				but not before this command,
 * ||										or
 *	(Xmin is committed &&					the row was inserted by a committed transaction, and
 *		(Xmax is null ||					the row has not been deleted, or
 *		 (Xmax == my-transaction &&			the row is being deleted by this transaction
 *		  Cmax >= my-command) ||			but it's not deleted "yet", or
 *		 (Xmax != my-transaction &&			the row was deleted by another transaction
 *		  Xmax is not committed))))			that has not been committed
 *
 *		mao says 17 march 1993:  the tests in this routine are correct;
 *		if you think they're not, you're wrong, and you should think
 *		about it again.  i know, it happened to me.  we don't need to
 *		check commit time against the start time of this transaction
 *		because 2ph locking protects us from doing the wrong thing.
 *		if you mess around here, you'll break serializability.  the only
 *		problem with this code is that it does the wrong thing for system
 *		catalog updates, because the catalogs aren't subject to 2ph, so
 *		the serializability guarantees we provide don't extend to xacts
 *		that do catalog accesses.  this is unfortunate, but not critical.
 */
bool
HeapTupleSatisfiesNow(HeapTupleHeader tuple, Buffer buffer)
{
	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
			return false;

		if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return false;
			if (!TransactionIdIsInProgress(xvac))
			{
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (!TransactionIdIsCurrentTransactionId(xvac))
			{
				if (TransactionIdIsInProgress(xvac))
					return false;
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_COMMITTED;
					SetBufferCommitInfoNeedsSave(buffer);
				}
				else
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
			}
		}
		else if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmin(tuple)))
		{
			if (HeapTupleHeaderGetCmin(tuple) >= GetCurrentCommandId())
				return false;	/* inserted after scan started */

			if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid */
				return true;

			if (tuple->t_infomask & HEAP_IS_LOCKED)		/* not deleter */
				return true;

			Assert(!(tuple->t_infomask & HEAP_XMAX_IS_MULTI));

			/* deleting subtransaction aborted? */
			if (TransactionIdDidAbort(HeapTupleHeaderGetXmax(tuple)))
			{
				tuple->t_infomask |= HEAP_XMAX_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return true;
			}

			Assert(TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)));

			if (HeapTupleHeaderGetCmax(tuple) >= GetCurrentCommandId())
				return true;	/* deleted after scan started */
			else
				return false;	/* deleted before scan started */
		}
		else if (TransactionIdIsInProgress(HeapTupleHeaderGetXmin(tuple)))
			return false;
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmin(tuple)))
		{
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/* it must have aborted or crashed */
			tuple->t_infomask |= HEAP_XMIN_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			return false;
		}
	}

	/* by here, the inserting transaction has committed */

	if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid or aborted */
		return true;

	if (tuple->t_infomask & HEAP_XMAX_COMMITTED)
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return true;
		return false;
	}

	if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
	{
		/* MultiXacts are currently only allowed to lock tuples */
		Assert(tuple->t_infomask & HEAP_IS_LOCKED);
		return true;
	}

	if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)))
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return true;
		if (HeapTupleHeaderGetCmax(tuple) >= GetCurrentCommandId())
			return true;		/* deleted after scan started */
		else
			return false;		/* deleted before scan started */
	}

	if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
		return true;

	if (!TransactionIdDidCommit(HeapTupleHeaderGetXmax(tuple)))
	{
		/* it must have aborted or crashed */
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return true;
	}

	/* xmax transaction committed */

	if (tuple->t_infomask & HEAP_IS_LOCKED)
	{
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return true;
	}

	tuple->t_infomask |= HEAP_XMAX_COMMITTED;
	SetBufferCommitInfoNeedsSave(buffer);
	return false;
}

/*
 * HeapTupleSatisfiesToast
 *		True iff heap tuple is valid as a TOAST row.
 *
 * This is a simplified version that only checks for VACUUM moving conditions.
 * It's appropriate for TOAST usage because TOAST really doesn't want to do
 * its own time qual checks; if you can see the main table row that contains
 * a TOAST reference, you should be able to see the TOASTed value.	However,
 * vacuuming a TOAST table is independent of the main table, and in case such
 * a vacuum fails partway through, we'd better do this much checking.
 *
 * Among other things, this means you can't do UPDATEs of rows in a TOAST
 * table.
 */
bool
HeapTupleSatisfiesToast(HeapTupleHeader tuple, Buffer buffer)
{
	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
			return false;

		if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return false;
			if (!TransactionIdIsInProgress(xvac))
			{
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (!TransactionIdIsCurrentTransactionId(xvac))
			{
				if (TransactionIdIsInProgress(xvac))
					return false;
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_COMMITTED;
					SetBufferCommitInfoNeedsSave(buffer);
				}
				else
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
			}
		}
	}

	/* otherwise assume the tuple is valid for TOAST. */
	return true;
}

/*
 * HeapTupleSatisfiesUpdate
 *
 *	Same logic as HeapTupleSatisfiesNow, but returns a more detailed result
 *	code, since UPDATE needs to know more than "is it visible?".  Also,
 *	tuples of my own xact are tested against the passed CommandId not
 *	CurrentCommandId.
 *
 *	The possible return codes are:
 *
 *	HeapTupleInvisible: the tuple didn't exist at all when the scan started,
 *	e.g. it was created by a later CommandId.
 *
 *	HeapTupleMayBeUpdated: The tuple is valid and visible, so it may be
 *	updated.
 *
 *	HeapTupleSelfUpdated: The tuple was updated by the current transaction,
 *	after the current scan started.
 *
 *	HeapTupleUpdated: The tuple was updated by a committed transaction.
 *
 *	HeapTupleBeingUpdated: The tuple is being updated by an in-progress
 *	transaction other than the current transaction.  (Note: this includes
 *	the case where the tuple is share-locked by a MultiXact, even if the
 *	MultiXact includes the current transaction.  Callers that want to
 *	distinguish that case must test for it themselves.)
 */
HTSU_Result
HeapTupleSatisfiesUpdate(HeapTupleHeader tuple, CommandId curcid,
						 Buffer buffer)
{
	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
			return HeapTupleInvisible;

		if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return HeapTupleInvisible;
			if (!TransactionIdIsInProgress(xvac))
			{
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return HeapTupleInvisible;
				}
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (!TransactionIdIsCurrentTransactionId(xvac))
			{
				if (TransactionIdIsInProgress(xvac))
					return HeapTupleInvisible;
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_COMMITTED;
					SetBufferCommitInfoNeedsSave(buffer);
				}
				else
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return HeapTupleInvisible;
				}
			}
		}
		else if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmin(tuple)))
		{
			if (HeapTupleHeaderGetCmin(tuple) >= curcid)
				return HeapTupleInvisible;		/* inserted after scan started */

			if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid */
				return HeapTupleMayBeUpdated;

			if (tuple->t_infomask & HEAP_IS_LOCKED)		/* not deleter */
				return HeapTupleMayBeUpdated;

			Assert(!(tuple->t_infomask & HEAP_XMAX_IS_MULTI));

			/* deleting subtransaction aborted? */
			if (TransactionIdDidAbort(HeapTupleHeaderGetXmax(tuple)))
			{
				tuple->t_infomask |= HEAP_XMAX_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return HeapTupleMayBeUpdated;
			}

			Assert(TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)));

			if (HeapTupleHeaderGetCmax(tuple) >= curcid)
				return HeapTupleSelfUpdated;	/* updated after scan started */
			else
				return HeapTupleInvisible;		/* updated before scan started */
		}
		else if (TransactionIdIsInProgress(HeapTupleHeaderGetXmin(tuple)))
			return HeapTupleInvisible;
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmin(tuple)))
		{
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/* it must have aborted or crashed */
			tuple->t_infomask |= HEAP_XMIN_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			return HeapTupleInvisible;
		}
	}

	/* by here, the inserting transaction has committed */

	if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid or aborted */
		return HeapTupleMayBeUpdated;

	if (tuple->t_infomask & HEAP_XMAX_COMMITTED)
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return HeapTupleMayBeUpdated;
		return HeapTupleUpdated;	/* updated by other */
	}

	if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
	{
		/* MultiXacts are currently only allowed to lock tuples */
		Assert(tuple->t_infomask & HEAP_IS_LOCKED);

		if (MultiXactIdIsRunning(HeapTupleHeaderGetXmax(tuple)))
			return HeapTupleBeingUpdated;
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return HeapTupleMayBeUpdated;
	}

	if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)))
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return HeapTupleMayBeUpdated;
		if (HeapTupleHeaderGetCmax(tuple) >= curcid)
			return HeapTupleSelfUpdated;		/* updated after scan started */
		else
			return HeapTupleInvisible;	/* updated before scan started */
	}

	if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
		return HeapTupleBeingUpdated;

	if (!TransactionIdDidCommit(HeapTupleHeaderGetXmax(tuple)))
	{
		/* it must have aborted or crashed */
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return HeapTupleMayBeUpdated;
	}

	/* xmax transaction committed */

	if (tuple->t_infomask & HEAP_IS_LOCKED)
	{
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return HeapTupleMayBeUpdated;
	}

	tuple->t_infomask |= HEAP_XMAX_COMMITTED;
	SetBufferCommitInfoNeedsSave(buffer);
	return HeapTupleUpdated;	/* updated by other */
}

/*
 * HeapTupleSatisfiesDirty
 *		True iff heap tuple is valid including effects of open transactions.
 *
 *	Here, we consider the effects of:
 *		all committed and in-progress transactions (as of the current instant)
 *		previous commands of this transaction
 *		changes made by the current command
 *
 * This is essentially like HeapTupleSatisfiesItself as far as effects of
 * the current transaction and committed/aborted xacts are concerned.
 * However, we also include the effects of other xacts still in progress.
 *
 * Returns extra information in the global variable SnapshotDirty, namely
 * xids of concurrent xacts that affected the tuple.  SnapshotDirty->xmin
 * is set to InvalidTransactionId if xmin is either committed good or
 * committed dead; or to xmin if that transaction is still in progress.
 * Similarly for SnapshotDirty->xmax.
 */
bool
HeapTupleSatisfiesDirty(HeapTupleHeader tuple, Buffer buffer)
{
	SnapshotDirty->xmin = SnapshotDirty->xmax = InvalidTransactionId;

	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
			return false;

		if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return false;
			if (!TransactionIdIsInProgress(xvac))
			{
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (!TransactionIdIsCurrentTransactionId(xvac))
			{
				if (TransactionIdIsInProgress(xvac))
					return false;
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_COMMITTED;
					SetBufferCommitInfoNeedsSave(buffer);
				}
				else
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					return false;
				}
			}
		}
		else if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmin(tuple)))
		{
			if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid */
				return true;

			if (tuple->t_infomask & HEAP_IS_LOCKED)		/* not deleter */
				return true;

			Assert(!(tuple->t_infomask & HEAP_XMAX_IS_MULTI));

			/* deleting subtransaction aborted? */
			if (TransactionIdDidAbort(HeapTupleHeaderGetXmax(tuple)))
			{
				tuple->t_infomask |= HEAP_XMAX_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return true;
			}

			Assert(TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)));

			return false;
		}
		else if (TransactionIdIsInProgress(HeapTupleHeaderGetXmin(tuple)))
		{
			SnapshotDirty->xmin = HeapTupleHeaderGetXmin(tuple);
			/* XXX shouldn't we fall through to look at xmax? */
			return true;		/* in insertion by other */
		}
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmin(tuple)))
		{
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/* it must have aborted or crashed */
			tuple->t_infomask |= HEAP_XMIN_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			return false;
		}
	}

	/* by here, the inserting transaction has committed */

	if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid or aborted */
		return true;

	if (tuple->t_infomask & HEAP_XMAX_COMMITTED)
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return true;
		return false;			/* updated by other */
	}

	if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
	{
		/* MultiXacts are currently only allowed to lock tuples */
		Assert(tuple->t_infomask & HEAP_IS_LOCKED);
		return true;
	}

	if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)))
	{
		if (tuple->t_infomask & HEAP_IS_LOCKED)
			return true;
		return false;
	}

	if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
	{
		SnapshotDirty->xmax = HeapTupleHeaderGetXmax(tuple);
		return true;
	}

	if (!TransactionIdDidCommit(HeapTupleHeaderGetXmax(tuple)))
	{
		/* it must have aborted or crashed */
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return true;
	}

	/* xmax transaction committed */

	if (tuple->t_infomask & HEAP_IS_LOCKED)
	{
		tuple->t_infomask |= HEAP_XMAX_INVALID;
		SetBufferCommitInfoNeedsSave(buffer);
		return true;
	}

	tuple->t_infomask |= HEAP_XMAX_COMMITTED;
	SetBufferCommitInfoNeedsSave(buffer);
	return false;				/* updated by other */
}

/*
 * HeapTupleSatisfiesSnapshot
 *		True iff heap tuple is valid for the given snapshot.
 *
 *	Here, we consider the effects of:
 *		all transactions committed as of the time of the given snapshot
 *		previous commands of this transaction
 *
 *	Does _not_ include:
 *		transactions shown as in-progress by the snapshot
 *		transactions started after the snapshot was taken
 *		changes made by the current command
 *
 * This is the same as HeapTupleSatisfiesNow, except that transactions that
 * were in progress or as yet unstarted when the snapshot was taken will
 * be treated as uncommitted, even if they have committed by now.
 *
 * (Notice, however, that the tuple status hint bits will be updated on the
 * basis of the true state of the transaction, even if we then pretend we
 * can't see it.)
 */
bool
HeapTupleSatisfiesSnapshot(HeapTupleHeader tuple, Snapshot snapshot,
						   Buffer buffer)
{
	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
		{
			LastHTSSReason = HeapTupleInvalid;
			return false;
		}

		if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
			{
				LastHTSSReason = HeapTupleCurrentTransaction;
				return false;				
			}
			if (!TransactionIdIsInProgress(xvac))
			{
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					LastHTSSReason = HeapTupleInvalid;
					return false;
				}
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (!TransactionIdIsCurrentTransactionId(xvac))
			{
				if (TransactionIdIsInProgress(xvac))
				{
					LastHTSSReason = HeapTupleInvalid;
					return false;
				}
				if (TransactionIdDidCommit(xvac))
				{
					tuple->t_infomask |= HEAP_XMIN_COMMITTED;
					SetBufferCommitInfoNeedsSave(buffer);
				}
				else
				{
					tuple->t_infomask |= HEAP_XMIN_INVALID;
					SetBufferCommitInfoNeedsSave(buffer);
					LastHTSSReason = HeapTupleInvalid;
					return false;
				}
			}
		}
		else if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmin(tuple)))
		{
			LastHTSSReason = HeapTupleCurrentTransaction;
			
			if (HeapTupleHeaderGetCmin(tuple) >= snapshot->curcid)
				return false;	/* inserted after scan started */

			if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid */
				return true;

			if (tuple->t_infomask & HEAP_IS_LOCKED)		/* not deleter */
				return true;

			Assert(!(tuple->t_infomask & HEAP_XMAX_IS_MULTI));

			/* deleting subtransaction aborted? */
			/* FIXME -- is this correct w.r.t. the cmax of the tuple? */
			if (TransactionIdDidAbort(HeapTupleHeaderGetXmax(tuple)))
			{
				tuple->t_infomask |= HEAP_XMAX_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return true;
			}

			Assert(TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)));

			if (HeapTupleHeaderGetCmax(tuple) >= snapshot->curcid)
				return true;	/* deleted after scan started */
			else
				return false;	/* deleted before scan started */
		}
		else if (TransactionIdIsInProgress(HeapTupleHeaderGetXmin(tuple)))
		{
			LastHTSSReason = HeapTupleInvalid;
			return false;
		}
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmin(tuple)))
		{
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/* it must have aborted or crashed */
			tuple->t_infomask |= HEAP_XMIN_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			LastHTSSReason = HeapTupleInvalid;
			return false;
		}
	}

	/*
	 * By here, the inserting transaction has committed - have to check
	 * when...
	 */
	if (XidInSnapshot(HeapTupleHeaderGetXmin(tuple), snapshot))
	{
		LastHTSSReason = HeapTupleNotInsertedYet;
		return false;			/* treat as still in progress */		
	}

	if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid or
												 * aborted */ 
	{
		LastHTSSReason = HeapTupleVisible;
		return true;		
	}

	if (tuple->t_infomask & HEAP_IS_LOCKED)
	{
		LastHTSSReason = HeapTupleVisible;
		return true;		
	}

	if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
	{
		/* MultiXacts are currently only allowed to lock tuples */
		Assert(tuple->t_infomask & HEAP_IS_LOCKED);
		LastHTSSReason = HeapTupleVisible;
		return true;
	}

	if (!(tuple->t_infomask & HEAP_XMAX_COMMITTED))
	{
		if (TransactionIdIsCurrentTransactionId(HeapTupleHeaderGetXmax(tuple)))
		{
			LastHTSSReason = HeapTupleCurrentTransaction;
			if (HeapTupleHeaderGetCmax(tuple) >= snapshot->curcid)
				return true;	/* deleted after scan started */
			else
				return false;	/* deleted before scan started */
		}

		if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
		{
			LastHTSSReason = HeapTupleVisible;
			return true;		
		}

		if (!TransactionIdDidCommit(HeapTupleHeaderGetXmax(tuple)))
		{
			/* it must have aborted or crashed */
			tuple->t_infomask |= HEAP_XMAX_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			LastHTSSReason = HeapTupleVisible;
			return true;
		}

		/* xmax transaction committed */
		tuple->t_infomask |= HEAP_XMAX_COMMITTED;
		SetBufferCommitInfoNeedsSave(buffer);
	}

	/*
	 * OK, the deleting transaction committed too ... but when?
	 */
	if (XidInSnapshot(HeapTupleHeaderGetXmax(tuple), snapshot))
	{
		LastHTSSReason = HeapTupleVisible;
		return true;			/* treat as still in progress */
	}

	LastHTSSReason = HeapTupleAlreadyDeleted;
	return false;
}


/*
 * HeapTupleSatisfiesVacuum
 *
 *	Determine the status of tuples for VACUUM purposes.  Here, what
 *	we mainly want to know is if a tuple is potentially visible to *any*
 *	running transaction.  If so, it can't be removed yet by VACUUM.
 *
 * OldestXmin is a cutoff XID (obtained from GetOldestXmin()).	Tuples
 * deleted by XIDs >= OldestXmin are deemed "recently dead"; they might
 * still be visible to some open transaction, so we can't remove them,
 * even if we see that the deleting transaction has committed.
 */
HTSV_Result
HeapTupleSatisfiesVacuum(HeapTupleHeader tuple, TransactionId OldestXmin,
						 Buffer buffer)
{
	/*
	 * Has inserting transaction committed?
	 *
	 * If the inserting transaction aborted, then the tuple was never visible
	 * to any other transaction, so we can delete it immediately.
	 */
	if (!(tuple->t_infomask & HEAP_XMIN_COMMITTED))
	{
		if (tuple->t_infomask & HEAP_XMIN_INVALID)
			return HEAPTUPLE_DEAD;
		else if (tuple->t_infomask & HEAP_MOVED_OFF)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return HEAPTUPLE_DELETE_IN_PROGRESS;
			if (TransactionIdIsInProgress(xvac))
				return HEAPTUPLE_DELETE_IN_PROGRESS;
			if (TransactionIdDidCommit(xvac))
			{
				tuple->t_infomask |= HEAP_XMIN_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return HEAPTUPLE_DEAD;
			}
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else if (tuple->t_infomask & HEAP_MOVED_IN)
		{
			TransactionId xvac = HeapTupleHeaderGetXvac(tuple);

			if (TransactionIdIsCurrentTransactionId(xvac))
				return HEAPTUPLE_INSERT_IN_PROGRESS;
			if (TransactionIdIsInProgress(xvac))
				return HEAPTUPLE_INSERT_IN_PROGRESS;
			if (TransactionIdDidCommit(xvac))
			{
				tuple->t_infomask |= HEAP_XMIN_COMMITTED;
				SetBufferCommitInfoNeedsSave(buffer);
			}
			else
			{
				tuple->t_infomask |= HEAP_XMIN_INVALID;
				SetBufferCommitInfoNeedsSave(buffer);
				return HEAPTUPLE_DEAD;
			}
		}
		else if (TransactionIdIsInProgress(HeapTupleHeaderGetXmin(tuple)))
		{
			if (tuple->t_infomask & HEAP_XMAX_INVALID)	/* xid invalid */
				return HEAPTUPLE_INSERT_IN_PROGRESS;
			if (tuple->t_infomask & HEAP_IS_LOCKED)
				return HEAPTUPLE_INSERT_IN_PROGRESS;
			/* inserted and then deleted by same xact */
			return HEAPTUPLE_DELETE_IN_PROGRESS;
		}
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmin(tuple)))
		{
			tuple->t_infomask |= HEAP_XMIN_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/*
			 * Not in Progress, Not Committed, so either Aborted or crashed
			 */
			tuple->t_infomask |= HEAP_XMIN_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			return HEAPTUPLE_DEAD;
		}
		/* Should only get here if we set XMIN_COMMITTED */
		Assert(tuple->t_infomask & HEAP_XMIN_COMMITTED);
	}

	/*
	 * Okay, the inserter committed, so it was good at some point.	Now what
	 * about the deleting transaction?
	 */
	if (tuple->t_infomask & HEAP_XMAX_INVALID)
		return HEAPTUPLE_LIVE;

	if (tuple->t_infomask & HEAP_IS_LOCKED)
	{
		/*
		 * "Deleting" xact really only locked it, so the tuple is live in any
		 * case.  However, we should make sure that either XMAX_COMMITTED or
		 * XMAX_INVALID gets set once the xact is gone, to reduce the costs
		 * of examining the tuple for future xacts.  Also, marking dead
		 * MultiXacts as invalid here provides defense against MultiXactId
		 * wraparound (see also comments in heap_freeze_tuple()).
		 */
		if (!(tuple->t_infomask & HEAP_XMAX_COMMITTED))
		{
			if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
			{
				if (MultiXactIdIsRunning(HeapTupleHeaderGetXmax(tuple)))
					return HEAPTUPLE_LIVE;
			}
			else
			{
				if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
					return HEAPTUPLE_LIVE;
			}

			/*
			 * We don't really care whether xmax did commit, abort or crash.
			 * We know that xmax did lock the tuple, but it did not and will
			 * never actually update it.
			 */
			tuple->t_infomask |= HEAP_XMAX_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		return HEAPTUPLE_LIVE;
	}

	if (tuple->t_infomask & HEAP_XMAX_IS_MULTI)
	{
		/* MultiXacts are currently only allowed to lock tuples */
		Assert(tuple->t_infomask & HEAP_IS_LOCKED);
		return HEAPTUPLE_LIVE;
	}

	if (!(tuple->t_infomask & HEAP_XMAX_COMMITTED))
	{
		if (TransactionIdIsInProgress(HeapTupleHeaderGetXmax(tuple)))
			return HEAPTUPLE_DELETE_IN_PROGRESS;
		else if (TransactionIdDidCommit(HeapTupleHeaderGetXmax(tuple)))
		{
			tuple->t_infomask |= HEAP_XMAX_COMMITTED;
			SetBufferCommitInfoNeedsSave(buffer);
		}
		else
		{
			/*
			 * Not in Progress, Not Committed, so either Aborted or crashed
			 */
			tuple->t_infomask |= HEAP_XMAX_INVALID;
			SetBufferCommitInfoNeedsSave(buffer);
			return HEAPTUPLE_LIVE;
		}
		/* Should only get here if we set XMAX_COMMITTED */
		Assert(tuple->t_infomask & HEAP_XMAX_COMMITTED);
	}

	/*
	 * Deleter committed, but check special cases.
	 */

	if (TransactionIdEquals(HeapTupleHeaderGetXmin(tuple),
							HeapTupleHeaderGetXmax(tuple)))
	{
		/*
		 * Inserter also deleted it, so it was never visible to anyone else.
		 * However, we can only remove it early if it's not an updated tuple;
		 * else its parent tuple is linking to it via t_ctid, and this tuple
		 * mustn't go away before the parent does.
		 */
		if (!(tuple->t_infomask & HEAP_UPDATED))
			return HEAPTUPLE_DEAD;
	}

	if (!TransactionIdPrecedes(HeapTupleHeaderGetXmax(tuple), OldestXmin))
	{
		/* deleting xact is too recent, tuple could still be visible */
		return HEAPTUPLE_RECENTLY_DEAD;
	}

	if (XidIsInvisibleToSomeSavedSnapshot(HeapTupleHeaderGetXmax(tuple)))
	{
		return HEAPTUPLE_RECENTLY_DEAD;
	}
	
	/* Otherwise, it's dead and removable */
	return HEAPTUPLE_DEAD;
}


/*
 * GetTransactionSnapshot
 *		Get the appropriate snapshot for a new query in a transaction.
 *
 * The SerializableSnapshot is the first one taken in a transaction.
 * In serializable mode we just use that one throughout the transaction.
 * In read-committed mode, we take a new snapshot each time we are called.
 *
 * Note that the return value points at static storage that will be modified
 * by future calls and by CommandCounterIncrement().  Callers should copy
 * the result with CopySnapshot() if it is to be used very long.
 */
Snapshot
GetTransactionSnapshot(void)
{
	/* First call in transaction? */
	if (SerializableSnapshot == NULL)
	{
		SerializableSnapshot = GetSnapshotData(&SerializableSnapshotData, true);
		return SerializableSnapshot;
	}

	if (IsXactIsoLevelSerializable)
		return SerializableSnapshot;

	LatestSnapshot = GetSnapshotData(&LatestSnapshotData, false);

	return LatestSnapshot;
}

bool
TransactionHasSerializableSnapshot(void)
{
	return (SerializableSnapshot != NULL);
}

/*
 * GetLatestSnapshot
 *		Get a snapshot that is up-to-date as of the current instant,
 *		even if we are executing in SERIALIZABLE mode.
 */
Snapshot
GetLatestSnapshot(void)
{
	/* Should not be first call in transaction */
	if (SerializableSnapshot == NULL)
		elog(ERROR, "no snapshot has been set");

	LatestSnapshot = GetSnapshotData(&LatestSnapshotData, false);

	return LatestSnapshot;
}

void
SetSerializableSnapshot(Snapshot snap)
{
	Snapshot newsnap = &SerializableSnapshotData;
	
	if (SerializableSnapshotData.xip == NULL)
	{
		/*
		 * Need to allocate memory; GetSnapshotData does this
		 * (but is also overkill.
		 */
		GetSnapshotData(&SerializableSnapshotData, false);
	}

	newsnap->xmin = snap->xmin;
	SetMyXmin(newsnap->xmin);
	newsnap->xmax = snap->xmax;
	newsnap->curcid = snap->curcid;
	newsnap->xcnt = snap->xcnt;

	memcpy(newsnap->xip, snap->xip,
		   snap->xcnt * sizeof(TransactionId));
	memcpy(newsnap->subxip, snap->subxip,
		   snap->subxcnt * sizeof(TransactionId));
	
	if (SerializableSnapshot == NULL)
	{
		SerializableSnapshot = &SerializableSnapshotData;
	}
}

/*
 * CopySnapshot
 *		Copy the given snapshot.
 *
 * The copy is palloc'd in the current memory context.
 *
 * Note that this will not work on "special" snapshots.
 */
Snapshot
CopySnapshot(Snapshot snapshot)
{
	Snapshot	newsnap;
	Size		subxipoff;
	Size		size;

	/* We allocate any XID arrays needed in the same palloc block. */
	size = subxipoff = sizeof(SnapshotData) +
		snapshot->xcnt * sizeof(TransactionId);
	if (snapshot->subxcnt > 0)
		size += snapshot->subxcnt * sizeof(TransactionId);

	newsnap = (Snapshot) palloc(size);
	memcpy(newsnap, snapshot, sizeof(SnapshotData));

	/* setup XID array */
	if (snapshot->xcnt > 0)
	{
		newsnap->xip = (TransactionId *) (newsnap + 1);
		memcpy(newsnap->xip, snapshot->xip,
			   snapshot->xcnt * sizeof(TransactionId));
	}
	else
		newsnap->xip = NULL;

	/* setup subXID array */
	if (snapshot->subxcnt > 0)
	{
		newsnap->subxip = (TransactionId *) ((char *) newsnap + subxipoff);
		memcpy(newsnap->subxip, snapshot->subxip,
			   snapshot->subxcnt * sizeof(TransactionId));
	}
	else
		newsnap->subxip = NULL;

	return newsnap;
}

/*
 * FreeSnapshot
 *		Free a snapshot previously copied with CopySnapshot.
 *
 * This is currently identical to pfree, but is provided for cleanliness.
 *
 * Do *not* apply this to the results of GetTransactionSnapshot or
 * GetLatestSnapshot.
 */
void
FreeSnapshot(Snapshot snapshot)
{
	pfree(snapshot);
}

/*
 * FreeXactSnapshot
 *		Free snapshot(s) at end of transaction.
 */
void
FreeXactSnapshot(void)
{
	/*
	 * We do not free the xip arrays for the static snapshot structs; they
	 * will be reused soon. So this is now just a state change to prevent
	 * outside callers from accessing the snapshots.
	 */
	SerializableSnapshot = NULL;
	LatestSnapshot = NULL;
	ActiveSnapshot = NULL;		/* just for cleanliness */
}

/*
 * XidInSnapshot
 *		Is the given XID still-in-progress according to the snapshot?
 *
 * Note: GetSnapshotData never stores either top xid or subxids of our own
 * backend into a snapshot, so these xids will not be reported as "running"
 * by this function.  This is OK for current uses, because we actually only
 * apply this for known-committed XIDs.
 */
bool
XidInSnapshot(TransactionId xid, Snapshot snapshot)
{
	uint32		i;

	/*
	 * Make a quick range check to eliminate most XIDs without looking at the
	 * xip arrays.	Note that this is OK even if we convert a subxact XID to
	 * its parent below, because a subxact with XID < xmin has surely also got
	 * a parent with XID < xmin, while one with XID >= xmax must belong to a
	 * parent that was not yet committed at the time of this snapshot.
	 */

	/* Any xid < xmin is not in-progress */
	if (TransactionIdPrecedes(xid, snapshot->xmin))
		return false;
	/* Any xid >= xmax is in-progress */
	if (TransactionIdFollowsOrEquals(xid, snapshot->xmax))
		return true;

	/*
	 * If the snapshot contains full subxact data, the fastest way to check
	 * things is just to compare the given XID against both subxact XIDs and
	 * top-level XIDs.	If the snapshot overflowed, we have to use pg_subtrans
	 * to convert a subxact XID to its parent XID, but then we need only look
	 * at top-level XIDs not subxacts.
	 */
	if (snapshot->subxcnt >= 0)
	{
		/* full data, so search subxip */
		int32		j;

		for (j = 0; j < snapshot->subxcnt; j++)
		{
			if (TransactionIdEquals(xid, snapshot->subxip[j]))
				return true;
		}

		/* not there, fall through to search xip[] */
	}
	else
	{
		/* overflowed, so convert xid to top-level */
		xid = SubTransGetTopmostTransaction(xid);

		/*
		 * If xid was indeed a subxact, we might now have an xid < xmin, so
		 * recheck to avoid an array scan.	No point in rechecking xmax.
		 */
		if (TransactionIdPrecedes(xid, snapshot->xmin))
			return false;
	}

	for (i = 0; i < snapshot->xcnt; i++)
	{
		if (TransactionIdEquals(xid, snapshot->xip[i]))
			return true;
	}

	return false;
}
