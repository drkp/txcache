/*-------------------------------------------------------------------------
 *
 * ssnap.h
 *   Saving and restoring snapshots; multiversion support for
 *   transactional caching
 *
 * IDENTIFICATION
 *	  $PostgreSQL$
 *
 *-------------------------------------------------------------------------
 */

#ifndef SSNAP_H
#define SSNAP_H

#include "postgres.h"
#include "utils/valint.h"
#include "utils/tqual.h"
#include "utils/xstamp.h"

#include <sys/time.h>

extern void SSnapInit();
extern Size SSnapShmemSize();
extern XStamp SaveSnapshot(struct timeval *tp);
extern Snapshot GetSavedSnapshot(XStamp id);
extern bool RemoveSavedSnapshot(XStamp id);
extern bool XidIsInvisibleToSomeSavedSnapshot(TransactionId xid);
extern void PrepareCommitXStamp();
extern XStamp GetCommitXStamp(TransactionId xid, bool invalidate);
extern XStamp XidToXStamp(TransactionId xid);
extern XStamp GetNextXStamp();
extern XStamp GetEarliestSavedSnapshotID();

#endif	/* SSNAP_H */
