/*-------------------------------------------------------------------------
 *
 * valint.c
 *   Validity interval utility functions; multiversion support for
 *   transactional caching
 *
 * IDENTIFICATION
 *	  $PostgreSQL$
 *
 *-------------------------------------------------------------------------
 */

#include "postgres.h"
#include "utils/ssnap.h"
#include "utils/valint.h"

#define MIN(x,y) (((x) < (y)) ? (x) : (y))
#define MAX(x,y) (((x) > (y)) ? (x) : (y))


const ValidityInterval InfiniteValidityInterval =
  { XStampMinusInfinity, XStampInfinity };
const ValidityInterval InvalidValidityInterval =
  { InvalidXStamp, InvalidXStamp };

bool
ValidityIntervalIsEmpty(ValidityInterval v)
{
	return (v.start < v.end);
}

ValidityInterval
ValidityIntervalIntersect(ValidityInterval a, ValidityInterval b)
{
	ValidityInterval r;
	r.start = MAX(a.start, b.start);
	r.end = MIN(a.end, b.end);
	return r;
}

/*
 * Create a new ValidityInterval equal to orig, but with changes
 * introduced by transaction x excluded.
 */
ValidityInterval
ValidityIntervalExclude(ValidityInterval orig, XStamp x, XStamp now)
{
	ValidityInterval r;

	if (x < now) {
		r.start = MAX(orig.start, x);
		r.end = orig.end;	
	} else {
		r.start = orig.start;
		r.end = MIN(orig.end, x);
	}
	return r;
}

ValidityInterval
ValidityIntervalExcludeInvisibleTuple(ValidityInterval inv,
									  HeapTuple heapTuple,
									  XStamp now)
{
	HTSS_Reason reason  = LastHTSSReason;
	XStamp xstamp;

	if (!XSTAMP_IS_VALID(now))
	{
		return inv;
	}
	
	switch (reason)
	{
		case HeapTupleVisible:
			/* shouldn't happen, we already know it was invisible */
			Assert(0);
			break;
			
		case HeapTupleCurrentTransaction:
			/* ignore this; it tells us nothing about validity */
			break;
			
		case HeapTupleInvalid:
			break;
			
		case HeapTupleNotInsertedYet:
			/* tuple's xmin excluded from validity interval */
			xstamp = XidToXStamp(HeapTupleHeaderGetXmin(heapTuple->t_data));
			if (xstamp != InvalidXStamp)
			{
				Assert(xstamp >= now);
				return ValidityIntervalExclude(inv, xstamp, now);
			}
			else
			{
				/* Xmin has no xstamp. Shouldn't
				 * happen. (Possible race if it *just*
				 * committed?)
				 */
				Assert(false);
			}
			break;
			
		case HeapTupleAlreadyDeleted:
			xstamp = XidToXStamp(HeapTupleHeaderGetXmax(heapTuple->t_data));
			if (xstamp != InvalidXStamp)
			{
				Assert(xstamp <= now);
				return ValidityIntervalExclude(inv, xstamp, now);
			}
			else
			{
				/*
				 * Xmin has no xstamp. Must be too old for a saved
				 * snapshot to exist, so ignore it for validity
				 * purposes.
				 */
			}
			break;
	}
	
	return inv;
}
