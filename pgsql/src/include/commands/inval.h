/*-------------------------------------------------------------------------
 *
 * inval.c
 *	  External cache invalidations
 *
 * IDENTIFICATION
 *	  $PostgreSQL$
 *
 *-------------------------------------------------------------------------
 */

#ifndef COMMANDS_INVAL_H
#define COMMANDS_INVAL_H

#include "access/skey.h"
#include "nodes/execnodes.h"
#include "utils/rel.h"
#include "utils/xstamp.h"

void InvalidationInit();
size_t InvalidationShmemSize();
void EnqueueInvalidate(const char *tag);
void AtCommit_Invalidate(XStamp xstamp);
void DropPendingInvalidations();
void SendNullInvalidation(XStamp xstamp);
void AddInvalidationTag(Relation rel, Relation index, int nKeys,
						ScanKey keys);
void InvalidateTag(Relation rel, Relation index,
				   IndexInfo *indexInfo, Datum *values, bool *isnull);
void InitQueryTagSet(MemoryContext ctx);
const char *GetQueryTagSet();

#endif	/* INVAL_H */
