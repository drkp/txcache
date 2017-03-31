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

#ifndef XSTAMP_H
#define XSTAMP_H

#include "postgres.h"

typedef uint32 XStamp;
#define InvalidXStamp       ((XStamp) 0)
#define XStampMinusInfinity ((XStamp) 1)
#define FirstXStamp         ((XStamp) 2)
#define XStampInfinity      ((XStamp) -1)

#define XSTAMP_IS_VALID(x) ((x != InvalidXStamp))

typedef struct CommitMap CommitMap;

extern XStamp CommitMapLookup(CommitMap *map, TransactionId xid);
extern void CommitMapAdd(CommitMap *map, TransactionId xid, XStamp xstamp);
extern Size CommitMapShmemSize();
extern void CommitMapExpire(CommitMap *map, XStamp earliestToKeep);
extern CommitMap * CommitMapShmemAlloc();


#endif /* XSTAMP_H */
