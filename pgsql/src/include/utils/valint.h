/*-------------------------------------------------------------------------
 *
 * valint.h
 *   Saving and restoring snapshots; multiversion support for
 *   transactional caching
 *
 * IDENTIFICATION
 *	  $PostgreSQL$
 *
 *-------------------------------------------------------------------------
 */

#ifndef VALINT_H
#define VALINT_H

#include "postgres.h"
#include "utils/xstamp.h"

struct HeapTupleData;

/*
 * The validity interval of a set of tuples is represented as the
 * XStamp of the latest transaction that created one of the tuples,
 * and the XStamp of the earliest transaction that deleted one of the
 * tuples.
 */
typedef struct
{
	XStamp start;
	XStamp end;
} ValidityInterval;

extern const ValidityInterval InfiniteValidityInterval;
extern const ValidityInterval InvalidValidityInterval;

extern bool ValidityIntervalIsEmpty(ValidityInterval v);
extern ValidityInterval ValidityIntervalIntersect(ValidityInterval a,
												  ValidityInterval b);
extern ValidityInterval ValidityIntervalExclude(ValidityInterval orig,
												XStamp x, XStamp now);

extern ValidityInterval ValidityIntervalExcludeInvisibleTuple(
	ValidityInterval inv,
	struct HeapTupleData *tuple,
	XStamp now);

static inline bool
ValidityIntervalCopyMaybe(ValidityInterval *out, ValidityInterval orig)
{
	if (out != NULL) {
		*out = orig;
		return TRUE;
	} else {
		return FALSE;
	}
}

static inline bool
ValidityIntervalSetInfiniteMaybe(ValidityInterval *out)
{
	if (out != NULL) {
		*out = InfiniteValidityInterval;
		return TRUE;
	} else {
		return FALSE;
	}
}

static inline bool
ValidityIntervalExcludeMaybe(ValidityInterval *out,
							 ValidityInterval orig,
							 XStamp x, XStamp now)
{
	if (out != NULL) {
		*out = ValidityIntervalExclude(orig, x, now);
		return TRUE;
	} else {
		return FALSE;
	}
}

static inline bool
ValidityIntervalIntersectMaybe(ValidityInterval *out,
							   ValidityInterval orig1,
							   ValidityInterval orig2)
{
	if (out != NULL) {
		*out = ValidityIntervalIntersect(orig1, orig2);
		return TRUE;
	} else {
		return FALSE;
	}
}

#endif	/* VALINT_H */
