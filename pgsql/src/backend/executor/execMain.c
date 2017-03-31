/*-------------------------------------------------------------------------
 *
 * execMain.c
 *	  top level executor interface routines
 *
 * INTERFACE ROUTINES
 *	ExecutorStart()
 *	ExecutorRun()
 *	ExecutorEnd()
 *
 *	The old ExecutorMain() has been replaced by ExecutorStart(),
 *	ExecutorRun() and ExecutorEnd()
 *
 *	These three procedures are the external interfaces to the executor.
 *	In each case, the query descriptor is required as an argument.
 *
 *	ExecutorStart() must be called at the beginning of execution of any
 *	query plan and ExecutorEnd() should always be called at the end of
 *	execution of a plan.
 *
 *	ExecutorRun accepts direction and count arguments that specify whether
 *	the plan is to be executed forwards, backwards, and for how many tuples.
 *
 * Portions Copyright (c) 1996-2006, PostgreSQL Global Development Group
 * Portions Copyright (c) 1994, Regents of the University of California
 *
 *
 * IDENTIFICATION
 *	  $PostgreSQL: pgsql/src/backend/executor/execMain.c,v 1.280.2.3 2008/08/08 17:01:26 tgl Exp $
 *
 *-------------------------------------------------------------------------
 */
#include "postgres.h"

#include "access/heapam.h"
#include "access/reloptions.h"
#include "access/transam.h"
#include "access/xact.h"
#include "catalog/heap.h"
#include "catalog/namespace.h"
#include "catalog/toasting.h"
#include "commands/tablespace.h"
#include "commands/trigger.h"
#include "executor/execdebug.h"
#include "executor/instrument.h"
#include "executor/nodeSubplan.h"
#include "miscadmin.h"
#include "optimizer/clauses.h"
#include "parser/parse_clause.h"
#include "parser/parse_expr.h"
#include "parser/parsetree.h"
#include "storage/smgr.h"
#include "utils/acl.h"
#include "utils/builtins.h"
#include "utils/lsyscache.h"
#include "utils/memutils.h"

#define REPORT_VALIDITY 0
#define FORCE_VALIDITY_COMPUTATION 0


typedef struct evalPlanQual
{
	Index		rti;
	EState	   *estate;
	PlanState  *planstate;
	struct evalPlanQual *next;	/* stack of active PlanQual plans */
	struct evalPlanQual *free;	/* list of free PlanQual plans */
} evalPlanQual;

/* decls for local routines only used within this module */
static void InitPlan(QueryDesc *queryDesc, int eflags);
static void initResultRelInfo(ResultRelInfo *resultRelInfo,
				  Index resultRelationIndex,
				  List *rangeTable,
				  CmdType operation,
				  bool doInstrument);
static void ExecCheckPlanOutput(Relation resultRel, List *targetList);
static TupleTableSlot *ExecutePlan(EState *estate, PlanState *planstate,
			CmdType operation,
			long numberTuples,
			ScanDirection direction,
			DestReceiver *dest);
static void ExecSelect(TupleTableSlot *slot,
		   DestReceiver *dest, EState *estate);
static void ExecInsert(TupleTableSlot *slot, ItemPointer tupleid,
		   TupleTableSlot *planSlot,
		   DestReceiver *dest, EState *estate);
static void ExecDelete(ItemPointer tupleid,
		   TupleTableSlot *planSlot,
		   DestReceiver *dest, EState *estate);
static void ExecUpdate(TupleTableSlot *slot, ItemPointer tupleid,
		   TupleTableSlot *planSlot,
		   DestReceiver *dest, EState *estate);
static void ExecProcessReturning(ProjectionInfo *projectReturning,
					 TupleTableSlot *tupleSlot,
					 TupleTableSlot *planSlot,
					 DestReceiver *dest);
static TupleTableSlot *EvalPlanQualNext(EState *estate);
static void EndEvalPlanQual(EState *estate);
static void ExecCheckRTEPerms(RangeTblEntry *rte);
static void ExecCheckXactReadOnly(Query *parsetree, bool testOnly);
static void EvalPlanQualStart(evalPlanQual *epq, EState *estate,
				  evalPlanQual *priorepq);
static void EvalPlanQualStop(evalPlanQual *epq);
static void OpenIntoRel(QueryDesc *queryDesc);
static void CloseIntoRel(QueryDesc *queryDesc);
static void intorel_startup(DestReceiver *self, int operation, TupleDesc typeinfo);
static void intorel_receive(TupleTableSlot *slot, DestReceiver *self);
static void intorel_shutdown(DestReceiver *self);
static void intorel_destroy(DestReceiver *self);

/* end of local decls */


/* ----------------------------------------------------------------
 *		ExecutorStart
 *
 *		This routine must be called at the beginning of any execution of any
 *		query plan
 *
 * Takes a QueryDesc previously created by CreateQueryDesc (it's not real
 * clear why we bother to separate the two functions, but...).	The tupDesc
 * field of the QueryDesc is filled in to describe the tuples that will be
 * returned, and the internal fields (estate and planstate) are set up.
 *
 * eflags contains flag bits as described in executor.h.
 *
 * NB: the CurrentMemoryContext when this is called will become the parent
 * of the per-query context used for this Executor invocation.
 * ----------------------------------------------------------------
 */
void
ExecutorStart(QueryDesc *queryDesc, int eflags)
{
	EState	   *estate;
	MemoryContext oldcontext;

	/* sanity checks: queryDesc must not be started already */
	Assert(queryDesc != NULL);
	Assert(queryDesc->estate == NULL);

	/*
	 * If the transaction is read-only, we need to check if any writes are
	 * planned to non-temporary tables.  EXPLAIN is considered read-only.
	 */
	if (XactReadOnly && !(eflags & EXEC_FLAG_EXPLAIN_ONLY))
		ExecCheckXactReadOnly(queryDesc->parsetree, FALSE);
	else
		ExecCheckXactReadOnly(queryDesc->parsetree, TRUE);

	/*
	 * Build EState, switch into per-query memory context for startup.
	 */
	estate = CreateExecutorState();
	queryDesc->estate = estate;

	oldcontext = MemoryContextSwitchTo(estate->es_query_cxt);

	/*
	 * Fill in parameters, if any, from queryDesc
	 */
	estate->es_param_list_info = queryDesc->params;

	if (queryDesc->plantree->nParamExec > 0)
		estate->es_param_exec_vals = (ParamExecData *)
			palloc0(queryDesc->plantree->nParamExec * sizeof(ParamExecData));

	/*
	 * Copy other important information into the EState
	 */
	estate->es_snapshot = queryDesc->snapshot;
	estate->es_crosscheck_snapshot = queryDesc->crosscheck_snapshot;
	estate->es_instrument = queryDesc->doInstrument;
	estate->es_xstamp = GetTransactionXStamp();
	estate->es_invalidityMask = InfiniteValidityInterval;
	estate->es_tupleValidity = InfiniteValidityInterval;

#if FORCE_VALIDITY_COMPUTATION
	if (GetEarliestSavedSnapshotID() != InvalidXStamp)
		estate->es_xstamp = GetNextXStamp();
#endif

	InitQueryTagSet(estate->es_query_cxt);
	SetLatestXStampAtTransactionStartIfUnset(GetNextXStamp());

	/*
	 * Initialize the plan state tree
	 */
	InitPlan(queryDesc, eflags);

	MemoryContextSwitchTo(oldcontext);
}

/* ----------------------------------------------------------------
 *		ExecutorRun
 *
 *		This is the main routine of the executor module. It accepts
 *		the query descriptor from the traffic cop and executes the
 *		query plan.
 *
 *		ExecutorStart must have been called already.
 *
 *		If direction is NoMovementScanDirection then nothing is done
 *		except to start up/shut down the destination.  Otherwise,
 *		we retrieve up to 'count' tuples in the specified direction.
 *
 *		Note: count = 0 is interpreted as no portal limit, i.e., run to
 *		completion.
 *
 * ----------------------------------------------------------------
 */
TupleTableSlot *
ExecutorRun(QueryDesc *queryDesc,
			ScanDirection direction, long count)
{
	EState	   *estate;
	CmdType		operation;
	DestReceiver *dest;
	bool		sendTuples;
	TupleTableSlot *result;
	MemoryContext oldcontext;
	ValidityInterval valint;

	/* sanity checks */
	Assert(queryDesc != NULL);

	estate = queryDesc->estate;

	Assert(estate != NULL);

	/*
	 * Switch into per-query memory context
	 */
	oldcontext = MemoryContextSwitchTo(estate->es_query_cxt);

	/*
	 * extract information from the query descriptor and the query feature.
	 */
	operation = queryDesc->operation;
	dest = queryDesc->dest;

	/*
	 * startup tuple receiver, if we will be emitting tuples
	 */
	estate->es_processed = 0;
	estate->es_lastoid = InvalidOid;

	sendTuples = (operation == CMD_SELECT ||
				  queryDesc->parsetree->returningList);

	if (sendTuples)
		(*dest->rStartup) (dest, operation, queryDesc->tupDesc);

	/*
	 * run plan
	 */
	if (ScanDirectionIsNoMovement(direction))
		result = NULL;
	else
		result = ExecutePlan(estate,
							 queryDesc->planstate,
							 operation,
							 count,
							 direction,
							 dest);

	/* Figure out validity intervals */
	if (XSTAMP_IS_VALID(estate->es_xstamp))
	{
		estate->es_finalValidity =
			ValidityIntervalIntersect(estate->es_invalidityMask,
									  estate->es_tupleValidity);
		if (estate->es_finalValidity.end == XStampInfinity)
		{
			estate->es_finalValidity.end
				= GetLatestXStampAtTransactionStart();
			estate->es_validityIsUnbounded = true;
		}
		else
			estate->es_validityIsUnbounded = false;

		if (estate->es_finalValidity.start == XStampMinusInfinity)
		{
			estate->es_finalValidity.start = GetEarliestSavedSnapshotID()-1;
		}
		
#if REPORT_VALIDITY
		ereport(WARNING,
				(errcode(ERRCODE_WARNING),
				 errmsg("tuple validity: %d  %d",
						estate->es_tupleValidity.start,
						estate->es_tupleValidity.end
					 )));
		ereport(WARNING,
				(errcode(ERRCODE_WARNING),
				 errmsg("invalidity mask: %d  %d",
						estate->es_invalidityMask.start,
						estate->es_invalidityMask.end
					 )));
		ereport(WARNING,
				(errcode(ERRCODE_WARNING),
				 errmsg("final validity interval: %d  %d",
						estate->es_finalValidity.start,
						estate->es_finalValidity.end
					 )));
#endif
	}

	/*
	 * shutdown tuple receiver, if we started it
	 */
	if (sendTuples)
		(*dest->rShutdown) (dest);

	MemoryContextSwitchTo(oldcontext);

	return result;
}

/* ----------------------------------------------------------------
 *		ExecutorEnd
 *
 *		This routine must be called at the end of execution of any
 *		query plan
 * ----------------------------------------------------------------
 */
void
ExecutorEnd(QueryDesc *queryDesc)
{
	EState	   *estate;
	MemoryContext oldcontext;

	/* sanity checks */
	Assert(queryDesc != NULL);

	estate = queryDesc->estate;

	Assert(estate != NULL);

	/*
	 * Switch into per-query memory context to run ExecEndPlan
	 */
	oldcontext = MemoryContextSwitchTo(estate->es_query_cxt);

	ExecEndPlan(queryDesc->planstate, estate);

	/*
	 * Close the SELECT INTO relation if any
	 */
	if (estate->es_select_into)
		CloseIntoRel(queryDesc);

	/*
	 * Must switch out of context before destroying it
	 */
	MemoryContextSwitchTo(oldcontext);

	/*
	 * Release EState and per-query memory context.  This should release
	 * everything the executor has allocated.
	 */
	FreeExecutorState(estate);

	/* Reset queryDesc fields that no longer point to anything */
	queryDesc->tupDesc = NULL;
	queryDesc->estate = NULL;
	queryDesc->planstate = NULL;
}

/* ----------------------------------------------------------------
 *		ExecutorRewind
 *
 *		This routine may be called on an open queryDesc to rewind it
 *		to the start.
 * ----------------------------------------------------------------
 */
void
ExecutorRewind(QueryDesc *queryDesc)
{
	EState	   *estate;
	MemoryContext oldcontext;

	/* sanity checks */
	Assert(queryDesc != NULL);

	estate = queryDesc->estate;

	Assert(estate != NULL);

	/* It's probably not sensible to rescan updating queries */
	Assert(queryDesc->operation == CMD_SELECT);

	/*
	 * Switch into per-query memory context
	 */
	oldcontext = MemoryContextSwitchTo(estate->es_query_cxt);

	/*
	 * rescan plan
	 */
	ExecReScan(queryDesc->planstate, NULL);

	MemoryContextSwitchTo(oldcontext);
}


/*
 * ExecCheckRTPerms
 *		Check access permissions for all relations listed in a range table.
 */
void
ExecCheckRTPerms(List *rangeTable)
{
	ListCell   *l;

	foreach(l, rangeTable)
	{
		RangeTblEntry *rte = lfirst(l);

		ExecCheckRTEPerms(rte);
	}
}

/*
 * ExecCheckRTEPerms
 *		Check access permissions for a single RTE.
 */
static void
ExecCheckRTEPerms(RangeTblEntry *rte)
{
	AclMode		requiredPerms;
	Oid			relOid;
	Oid			userid;

	/*
	 * Only plain-relation RTEs need to be checked here.  Subquery RTEs are
	 * checked by ExecInitSubqueryScan if the subquery is still a separate
	 * subquery --- if it's been pulled up into our query level then the RTEs
	 * are in our rangetable and will be checked here. Function RTEs are
	 * checked by init_fcache when the function is prepared for execution.
	 * Join and special RTEs need no checks.
	 */
	if (rte->rtekind != RTE_RELATION)
		return;

	/*
	 * No work if requiredPerms is empty.
	 */
	requiredPerms = rte->requiredPerms;
	if (requiredPerms == 0)
		return;

	relOid = rte->relid;

	/*
	 * userid to check as: current user unless we have a setuid indication.
	 *
	 * Note: GetUserId() is presently fast enough that there's no harm in
	 * calling it separately for each RTE.	If that stops being true, we could
	 * call it once in ExecCheckRTPerms and pass the userid down from there.
	 * But for now, no need for the extra clutter.
	 */
	userid = rte->checkAsUser ? rte->checkAsUser : GetUserId();

	/*
	 * We must have *all* the requiredPerms bits, so use aclmask not aclcheck.
	 */
	if (pg_class_aclmask(relOid, userid, requiredPerms, ACLMASK_ALL)
		!= requiredPerms)
		aclcheck_error(ACLCHECK_NO_PRIV, ACL_KIND_CLASS,
					   get_rel_name(relOid));
}

/*
 * Check that the query does not imply any writes to non-temp tables.
 */
static void
ExecCheckXactReadOnly(Query *parsetree, bool testOnly)
{
	ListCell   *l;

	/*
	 * CREATE TABLE AS or SELECT INTO?
	 *
	 * XXX should we allow this if the destination is temp?
	 */
	if (parsetree->into != NULL)
		goto fail;

	/* Fail if write permissions are requested on any non-temp table */
	foreach(l, parsetree->rtable)
	{
		RangeTblEntry *rte = lfirst(l);

		if (rte->rtekind == RTE_SUBQUERY)
		{
			ExecCheckXactReadOnly(rte->subquery, testOnly);
			continue;
		}

		if (rte->rtekind != RTE_RELATION)
			continue;

		if ((rte->requiredPerms & (~ACL_SELECT)) == 0)
			continue;

		if (isTempNamespace(get_rel_namespace(rte->relid)))
			continue;

		goto fail;
	}

	return;

fail:
	if (!testOnly) {
		ereport(ERROR,
				(errcode(ERRCODE_READ_ONLY_SQL_TRANSACTION),
				 errmsg("transaction is read-only")));		
	} else {
		XactWrote = TRUE;
	}
}


/* ----------------------------------------------------------------
 *		InitPlan
 *
 *		Initializes the query plan: open files, allocate storage
 *		and start up the rule manager
 * ----------------------------------------------------------------
 */
static void
InitPlan(QueryDesc *queryDesc, int eflags)
{
	CmdType		operation = queryDesc->operation;
	Query	   *parseTree = queryDesc->parsetree;
	Plan	   *plan = queryDesc->plantree;
	EState	   *estate = queryDesc->estate;
	PlanState  *planstate;
	List	   *rangeTable;
	TupleDesc	tupType;
	ListCell   *l;

	/*
	 * Do permissions checks.  It's sufficient to examine the query's top
	 * rangetable here --- subplan RTEs will be checked during
	 * ExecInitSubPlan().
	 */
	ExecCheckRTPerms(parseTree->rtable);

	/*
	 * get information from query descriptor
	 */
	rangeTable = parseTree->rtable;

	/*
	 * initialize the node's execution state
	 */
	estate->es_range_table = rangeTable;

	/*
	 * if there is a result relation, initialize result relation stuff
	 */
	if (parseTree->resultRelation)
	{
		List	   *resultRelations = parseTree->resultRelations;
		int			numResultRelations;
		ResultRelInfo *resultRelInfos;

		if (resultRelations != NIL)
		{
			/*
			 * Multiple result relations (due to inheritance)
			 * parseTree->resultRelations identifies them all
			 */
			ResultRelInfo *resultRelInfo;

			numResultRelations = list_length(resultRelations);
			resultRelInfos = (ResultRelInfo *)
				palloc(numResultRelations * sizeof(ResultRelInfo));
			resultRelInfo = resultRelInfos;
			foreach(l, resultRelations)
			{
				initResultRelInfo(resultRelInfo,
								  lfirst_int(l),
								  rangeTable,
								  operation,
								  estate->es_instrument);
				resultRelInfo++;
			}
		}
		else
		{
			/*
			 * Single result relation identified by parseTree->resultRelation
			 */
			numResultRelations = 1;
			resultRelInfos = (ResultRelInfo *) palloc(sizeof(ResultRelInfo));
			initResultRelInfo(resultRelInfos,
							  parseTree->resultRelation,
							  rangeTable,
							  operation,
							  estate->es_instrument);
		}

		estate->es_result_relations = resultRelInfos;
		estate->es_num_result_relations = numResultRelations;
		/* Initialize to first or only result rel */
		estate->es_result_relation_info = resultRelInfos;
	}
	else
	{
		/*
		 * if no result relation, then set state appropriately
		 */
		estate->es_result_relations = NULL;
		estate->es_num_result_relations = 0;
		estate->es_result_relation_info = NULL;
	}

	/*
	 * Detect whether we're doing SELECT INTO.  If so, set the es_into_oids
	 * flag appropriately so that the plan tree will be initialized with the
	 * correct tuple descriptors.  (Other SELECT INTO stuff comes later.)
	 */
	estate->es_select_into = false;
	if (operation == CMD_SELECT && parseTree->into != NULL)
	{
		estate->es_select_into = true;
		estate->es_into_oids = interpretOidsOption(parseTree->intoOptions);
	}

	/*
	 * Have to lock relations selected FOR UPDATE/FOR SHARE
	 */
	estate->es_rowMarks = NIL;
	foreach(l, parseTree->rowMarks)
	{
		RowMarkClause *rc = (RowMarkClause *) lfirst(l);
		Oid			relid = getrelid(rc->rti, rangeTable);
		Relation	relation;
		ExecRowMark *erm;

		relation = heap_open(relid, RowShareLock);
		erm = (ExecRowMark *) palloc(sizeof(ExecRowMark));
		erm->relation = relation;
		erm->rti = rc->rti;
		erm->forUpdate = rc->forUpdate;
		erm->noWait = rc->noWait;
		snprintf(erm->resname, sizeof(erm->resname), "ctid%u", rc->rti);
		estate->es_rowMarks = lappend(estate->es_rowMarks, erm);
	}

	/*
	 * initialize the executor "tuple" table.  We need slots for all the plan
	 * nodes, plus possibly output slots for the junkfilter(s). At this point
	 * we aren't sure if we need junkfilters, so just add slots for them
	 * unconditionally.  Also, if it's not a SELECT, set up a slot for use for
	 * trigger output tuples.
	 */
	{
		int			nSlots = ExecCountSlotsNode(plan);

		if (parseTree->resultRelations != NIL)
			nSlots += list_length(parseTree->resultRelations);
		else
			nSlots += 1;
		if (operation != CMD_SELECT)
			nSlots++;			/* for es_trig_tuple_slot */
		if (parseTree->returningLists)
			nSlots++;			/* for RETURNING projection */

		estate->es_tupleTable = ExecCreateTupleTable(nSlots);

		if (operation != CMD_SELECT)
			estate->es_trig_tuple_slot =
				ExecAllocTableSlot(estate->es_tupleTable);
	}

	/* mark EvalPlanQual not active */
	estate->es_topPlan = plan;
	estate->es_evalPlanQual = NULL;
	estate->es_evTupleNull = NULL;
	estate->es_evTuple = NULL;
	estate->es_useEvalPlan = false;

	/*
	 * initialize the private state information for all the nodes in the query
	 * tree.  This opens files, allocates storage and leaves us ready to start
	 * processing tuples.
	 */
	planstate = ExecInitNode(plan, estate, eflags);

	/*
	 * Get the tuple descriptor describing the type of tuples to return. (this
	 * is especially important if we are creating a relation with "SELECT
	 * INTO")
	 */
	tupType = ExecGetResultType(planstate);

	/*
	 * Initialize the junk filter if needed.  SELECT and INSERT queries need a
	 * filter if there are any junk attrs in the tlist.  INSERT and SELECT
	 * INTO also need a filter if the plan may return raw disk tuples (else
	 * heap_insert will be scribbling on the source relation!). UPDATE and
	 * DELETE always need a filter, since there's always a junk 'ctid'
	 * attribute present --- no need to look first.
	 *
	 * This section of code is also a convenient place to verify that the
	 * output of an INSERT or UPDATE matches the target table(s).
	 */
	{
		bool		junk_filter_needed = false;
		ListCell   *tlist;

		switch (operation)
		{
			case CMD_SELECT:
			case CMD_INSERT:
				foreach(tlist, plan->targetlist)
				{
					TargetEntry *tle = (TargetEntry *) lfirst(tlist);

					if (tle->resjunk)
					{
						junk_filter_needed = true;
						break;
					}
				}
				if (!junk_filter_needed &&
					(operation == CMD_INSERT || estate->es_select_into) &&
					ExecMayReturnRawTuples(planstate))
					junk_filter_needed = true;
				break;
			case CMD_UPDATE:
			case CMD_DELETE:
				junk_filter_needed = true;
				break;
			default:
				break;
		}

		if (junk_filter_needed)
		{
			/*
			 * If there are multiple result relations, each one needs its own
			 * junk filter.  Note this is only possible for UPDATE/DELETE, so
			 * we can't be fooled by some needing a filter and some not.
			 */
			if (parseTree->resultRelations != NIL)
			{
				PlanState **appendplans;
				int			as_nplans;
				ResultRelInfo *resultRelInfo;
				int			i;

				/* Top plan had better be an Append here. */
				Assert(IsA(plan, Append));
				Assert(((Append *) plan)->isTarget);
				Assert(IsA(planstate, AppendState));
				appendplans = ((AppendState *) planstate)->appendplans;
				as_nplans = ((AppendState *) planstate)->as_nplans;
				Assert(as_nplans == estate->es_num_result_relations);
				resultRelInfo = estate->es_result_relations;
				for (i = 0; i < as_nplans; i++)
				{
					PlanState  *subplan = appendplans[i];
					JunkFilter *j;

					if (operation == CMD_UPDATE)
						ExecCheckPlanOutput(resultRelInfo->ri_RelationDesc,
											subplan->plan->targetlist);

					j = ExecInitJunkFilter(subplan->plan->targetlist,
							resultRelInfo->ri_RelationDesc->rd_att->tdhasoid,
								  ExecAllocTableSlot(estate->es_tupleTable));
					resultRelInfo->ri_junkFilter = j;
					resultRelInfo++;
				}

				/*
				 * Set active junkfilter too; at this point ExecInitAppend has
				 * already selected an active result relation...
				 */
				estate->es_junkFilter =
					estate->es_result_relation_info->ri_junkFilter;
			}
			else
			{
				/* Normal case with just one JunkFilter */
				JunkFilter *j;

				if (operation == CMD_INSERT || operation == CMD_UPDATE)
					ExecCheckPlanOutput(estate->es_result_relation_info->ri_RelationDesc,
										planstate->plan->targetlist);

				j = ExecInitJunkFilter(planstate->plan->targetlist,
									   tupType->tdhasoid,
								  ExecAllocTableSlot(estate->es_tupleTable));
				estate->es_junkFilter = j;
				if (estate->es_result_relation_info)
					estate->es_result_relation_info->ri_junkFilter = j;

				/* For SELECT, want to return the cleaned tuple type */
				if (operation == CMD_SELECT)
					tupType = j->jf_cleanTupType;
			}
		}
		else
		{
			if (operation == CMD_INSERT)
				ExecCheckPlanOutput(estate->es_result_relation_info->ri_RelationDesc,
									planstate->plan->targetlist);

			estate->es_junkFilter = NULL;
		}
	}

	/*
	 * Initialize RETURNING projections if needed.
	 */
	if (parseTree->returningLists)
	{
		TupleTableSlot *slot;
		ExprContext *econtext;
		ResultRelInfo *resultRelInfo;

		/*
		 * We set QueryDesc.tupDesc to be the RETURNING rowtype in this case.
		 * We assume all the sublists will generate the same output tupdesc.
		 */
		tupType = ExecTypeFromTL((List *) linitial(parseTree->returningLists),
								 false);

		/* Set up a slot for the output of the RETURNING projection(s) */
		slot = ExecAllocTableSlot(estate->es_tupleTable);
		ExecSetSlotDescriptor(slot, tupType);
		/* Need an econtext too */
		econtext = CreateExprContext(estate);

		/*
		 * Build a projection for each result rel.	Note that any SubPlans in
		 * the RETURNING lists get attached to the topmost plan node.
		 */
		Assert(list_length(parseTree->returningLists) == estate->es_num_result_relations);
		resultRelInfo = estate->es_result_relations;
		foreach(l, parseTree->returningLists)
		{
			List	   *rlist = (List *) lfirst(l);
			List	   *rliststate;

			rliststate = (List *) ExecInitExpr((Expr *) rlist, planstate);
			resultRelInfo->ri_projectReturning =
				ExecBuildProjectionInfo(rliststate, econtext, slot,
									   resultRelInfo->ri_RelationDesc->rd_att);
			resultRelInfo++;
		}

		/*
		 * Because we already ran ExecInitNode() for the top plan node, any
		 * subplans we just attached to it won't have been initialized; so we
		 * have to do it here.	(Ugly, but the alternatives seem worse.)
		 */
		foreach(l, planstate->subPlan)
		{
			SubPlanState *sstate = (SubPlanState *) lfirst(l);

			Assert(IsA(sstate, SubPlanState));
			if (sstate->planstate == NULL)		/* already inited? */
				ExecInitSubPlan(sstate, estate, eflags);
		}
	}

	queryDesc->tupDesc = tupType;
	queryDesc->planstate = planstate;

	/*
	 * If doing SELECT INTO, initialize the "into" relation.  We must wait
	 * till now so we have the "clean" result tuple type to create the new
	 * table from.
	 *
	 * If EXPLAIN, skip creating the "into" relation.
	 */
	if (estate->es_select_into && !(eflags & EXEC_FLAG_EXPLAIN_ONLY))
		OpenIntoRel(queryDesc);
}

/*
 * Initialize ResultRelInfo data for one result relation
 */
static void
initResultRelInfo(ResultRelInfo *resultRelInfo,
				  Index resultRelationIndex,
				  List *rangeTable,
				  CmdType operation,
				  bool doInstrument)
{
	Oid			resultRelationOid;
	Relation	resultRelationDesc;

	resultRelationOid = getrelid(resultRelationIndex, rangeTable);
	resultRelationDesc = heap_open(resultRelationOid, RowExclusiveLock);

	switch (resultRelationDesc->rd_rel->relkind)
	{
		case RELKIND_SEQUENCE:
			ereport(ERROR,
					(errcode(ERRCODE_WRONG_OBJECT_TYPE),
					 errmsg("cannot change sequence \"%s\"",
							RelationGetRelationName(resultRelationDesc))));
			break;
		case RELKIND_TOASTVALUE:
			ereport(ERROR,
					(errcode(ERRCODE_WRONG_OBJECT_TYPE),
					 errmsg("cannot change TOAST relation \"%s\"",
							RelationGetRelationName(resultRelationDesc))));
			break;
		case RELKIND_VIEW:
			ereport(ERROR,
					(errcode(ERRCODE_WRONG_OBJECT_TYPE),
					 errmsg("cannot change view \"%s\"",
							RelationGetRelationName(resultRelationDesc))));
			break;
	}

	MemSet(resultRelInfo, 0, sizeof(ResultRelInfo));
	resultRelInfo->type = T_ResultRelInfo;
	resultRelInfo->ri_RangeTableIndex = resultRelationIndex;
	resultRelInfo->ri_RelationDesc = resultRelationDesc;
	resultRelInfo->ri_NumIndices = 0;
	resultRelInfo->ri_IndexRelationDescs = NULL;
	resultRelInfo->ri_IndexRelationInfo = NULL;
	/* make a copy so as not to depend on relcache info not changing... */
	resultRelInfo->ri_TrigDesc = CopyTriggerDesc(resultRelationDesc->trigdesc);
	if (resultRelInfo->ri_TrigDesc)
	{
		int			n = resultRelInfo->ri_TrigDesc->numtriggers;

		resultRelInfo->ri_TrigFunctions = (FmgrInfo *)
			palloc0(n * sizeof(FmgrInfo));
		if (doInstrument)
			resultRelInfo->ri_TrigInstrument = InstrAlloc(n);
		else
			resultRelInfo->ri_TrigInstrument = NULL;
	}
	else
	{
		resultRelInfo->ri_TrigFunctions = NULL;
		resultRelInfo->ri_TrigInstrument = NULL;
	}
	resultRelInfo->ri_ConstraintExprs = NULL;
	resultRelInfo->ri_junkFilter = NULL;
	resultRelInfo->ri_projectReturning = NULL;

	/*
	 * If there are indices on the result relation, open them and save
	 * descriptors in the result relation info, so that we can add new index
	 * entries for the tuples we add/update.  We need not do this for a
	 * DELETE, however, since deletion doesn't affect indexes.
	 *
	 * TxCache: actually, we *do* need to open the indexes for a
	 * delete in case we need to generate the appropriate invalidation
	 * tags.
	 */
	if (resultRelationDesc->rd_rel->relhasindex)
		ExecOpenIndices(resultRelInfo);
}

/*
 *		ExecContextForcesOids
 *
 * This is pretty grotty: when doing INSERT, UPDATE, or SELECT INTO,
 * we need to ensure that result tuples have space for an OID iff they are
 * going to be stored into a relation that has OIDs.  In other contexts
 * we are free to choose whether to leave space for OIDs in result tuples
 * (we generally don't want to, but we do if a physical-tlist optimization
 * is possible).  This routine checks the plan context and returns TRUE if the
 * choice is forced, FALSE if the choice is not forced.  In the TRUE case,
 * *hasoids is set to the required value.
 *
 * One reason this is ugly is that all plan nodes in the plan tree will emit
 * tuples with space for an OID, though we really only need the topmost node
 * to do so.  However, node types like Sort don't project new tuples but just
 * return their inputs, and in those cases the requirement propagates down
 * to the input node.  Eventually we might make this code smart enough to
 * recognize how far down the requirement really goes, but for now we just
 * make all plan nodes do the same thing if the top level forces the choice.
 *
 * We assume that estate->es_result_relation_info is already set up to
 * describe the target relation.  Note that in an UPDATE that spans an
 * inheritance tree, some of the target relations may have OIDs and some not.
 * We have to make the decisions on a per-relation basis as we initialize
 * each of the child plans of the topmost Append plan.
 *
 * SELECT INTO is even uglier, because we don't have the INTO relation's
 * descriptor available when this code runs; we have to look aside at a
 * flag set by InitPlan().
 */
bool
ExecContextForcesOids(PlanState *planstate, bool *hasoids)
{
	if (planstate->state->es_select_into)
	{
		*hasoids = planstate->state->es_into_oids;
		return true;
	}
	else
	{
		ResultRelInfo *ri = planstate->state->es_result_relation_info;

		if (ri != NULL)
		{
			Relation	rel = ri->ri_RelationDesc;

			if (rel != NULL)
			{
				*hasoids = rel->rd_rel->relhasoids;
				return true;
			}
		}
	}

	return false;
}

/*
 * Verify that the tuples to be produced by INSERT or UPDATE match the
 * target relation's rowtype
 *
 * We do this to guard against stale plans.  If plan invalidation is
 * functioning properly then we should never get a failure here, but better
 * safe than sorry.  Note that this is called after we have obtained lock
 * on the target rel, so the rowtype can't change underneath us.
 *
 * The plan output is represented by its targetlist, because that makes
 * handling the dropped-column case easier.
 */
static void
ExecCheckPlanOutput(Relation resultRel, List *targetList)
{
	TupleDesc	resultDesc = RelationGetDescr(resultRel);
	int			attno = 0;
	ListCell   *lc;

	foreach(lc, targetList)
	{
		TargetEntry *tle = (TargetEntry *) lfirst(lc);
		Form_pg_attribute attr;

		if (tle->resjunk)
			continue;			/* ignore junk tlist items */

		if (attno >= resultDesc->natts)
			ereport(ERROR,
					(errcode(ERRCODE_DATATYPE_MISMATCH),
					 errmsg("table row type and query-specified row type do not match"),
					 errdetail("Query has too many columns.")));
		attr = resultDesc->attrs[attno++];

		if (!attr->attisdropped)
		{
			/* Normal case: demand type match */
			if (exprType((Node *) tle->expr) != attr->atttypid)
				ereport(ERROR,
						(errcode(ERRCODE_DATATYPE_MISMATCH),
						 errmsg("table row type and query-specified row type do not match"),
						 errdetail("Table has type %s at ordinal position %d, but query expects %s.",
								   format_type_be(attr->atttypid),
								   attno,
								   format_type_be(exprType((Node *) tle->expr)))));
		}
		else
		{
			/*
			 * For a dropped column, we can't check atttypid (it's likely 0).
			 * In any case the planner has most likely inserted an INT4 null.
			 * What we insist on is just *some* NULL constant.
			 */
			if (!IsA(tle->expr, Const) ||
				!((Const *) tle->expr)->constisnull)
				ereport(ERROR,
						(errcode(ERRCODE_DATATYPE_MISMATCH),
						 errmsg("table row type and query-specified row type do not match"),
						 errdetail("Query provides a value for a dropped column at ordinal position %d.",
								   attno)));
		}
	}
	if (attno != resultDesc->natts)
		ereport(ERROR,
				(errcode(ERRCODE_DATATYPE_MISMATCH),
				 errmsg("table row type and query-specified row type do not match"),
				 errdetail("Query has too few columns.")));
}


/* ----------------------------------------------------------------
 *		ExecEndPlan
 *
 *		Cleans up the query plan -- closes files and frees up storage
 *
 * NOTE: we are no longer very worried about freeing storage per se
 * in this code; FreeExecutorState should be guaranteed to release all
 * memory that needs to be released.  What we are worried about doing
 * is closing relations and dropping buffer pins.  Thus, for example,
 * tuple tables must be cleared or dropped to ensure pins are released.
 * ----------------------------------------------------------------
 */
void
ExecEndPlan(PlanState *planstate, EState *estate)
{
	ResultRelInfo *resultRelInfo;
	int			i;
	ListCell   *l;

	/*
	 * shut down any PlanQual processing we were doing
	 */
	if (estate->es_evalPlanQual != NULL)
		EndEvalPlanQual(estate);

	/*
	 * shut down the node-type-specific query processing
	 */
	ExecEndNode(planstate);

	/*
	 * destroy the executor "tuple" table.
	 */
	ExecDropTupleTable(estate->es_tupleTable, true);
	estate->es_tupleTable = NULL;

	/*
	 * close the result relation(s) if any, but hold locks until xact commit.
	 */
	resultRelInfo = estate->es_result_relations;
	for (i = estate->es_num_result_relations; i > 0; i--)
	{
		/* Close indices and then the relation itself */
		ExecCloseIndices(resultRelInfo);
		heap_close(resultRelInfo->ri_RelationDesc, NoLock);
		resultRelInfo++;
	}

	/*
	 * close any relations selected FOR UPDATE/FOR SHARE, again keeping locks
	 */
	foreach(l, estate->es_rowMarks)
	{
		ExecRowMark *erm = lfirst(l);

		heap_close(erm->relation, NoLock);
	}
}

/* ----------------------------------------------------------------
 *		ExecutePlan
 *
 *		processes the query plan to retrieve 'numberTuples' tuples in the
 *		direction specified.
 *
 *		Retrieves all tuples if numberTuples is 0
 *
 *		result is either a slot containing the last tuple in the case
 *		of a SELECT or NULL otherwise.
 *
 * Note: the ctid attribute is a 'junk' attribute that is removed before the
 * user can see it
 * ----------------------------------------------------------------
 */
static TupleTableSlot *
ExecutePlan(EState *estate,
			PlanState *planstate,
			CmdType operation,
			long numberTuples,
			ScanDirection direction,
			DestReceiver *dest)
{
	JunkFilter *junkfilter;
	TupleTableSlot *planSlot;
	TupleTableSlot *slot;
	ItemPointer tupleid = NULL;
	ItemPointerData tuple_ctid;
	long		current_tuple_count;
	TupleTableSlot *result;

	/*
	 * initialize local variables
	 */
	current_tuple_count = 0;
	result = NULL;

	/*
	 * Set the direction.
	 */
	estate->es_direction = direction;

	/*
	 * Process BEFORE EACH STATEMENT triggers
	 */
	switch (operation)
	{
		case CMD_UPDATE:
			ExecBSUpdateTriggers(estate, estate->es_result_relation_info);
			break;
		case CMD_DELETE:
			ExecBSDeleteTriggers(estate, estate->es_result_relation_info);
			break;
		case CMD_INSERT:
			ExecBSInsertTriggers(estate, estate->es_result_relation_info);
			break;
		default:
			/* do nothing */
			break;
	}

	/*
	 * Loop until we've processed the proper number of tuples from the plan.
	 */

	for (;;)
	{
		/* Reset the per-output-tuple exprcontext */
		ResetPerTupleExprContext(estate);

		/*
		 * Execute the plan and obtain a tuple
		 */
lnext:	;
		if (estate->es_useEvalPlan)
		{
			planSlot = EvalPlanQualNext(estate);
			if (TupIsNull(planSlot))
				planSlot = ExecProcNode(planstate);
		}
		else
			planSlot = ExecProcNode(planstate);

		/*
		 * if the tuple is null, then we assume there is nothing more to
		 * process so we just return null...
		 */
		if (TupIsNull(planSlot))
		{
			result = NULL;
			break;
		}
		slot = planSlot;

		if (XSTAMP_IS_VALID(estate->es_xstamp))
		{
			ValidityInterval tupVal = GetTupleValidity(slot);				
			estate->es_tupleValidity =
				ValidityIntervalIntersect(tupVal,
										  estate->es_tupleValidity);
		}		
		
		/*
		 * if we have a junk filter, then project a new tuple with the junk
		 * removed.
		 *
		 * Store this new "clean" tuple in the junkfilter's resultSlot.
		 * (Formerly, we stored it back over the "dirty" tuple, which is WRONG
		 * because that tuple slot has the wrong descriptor.)
		 *
		 * Also, extract all the junk information we need.
		 */
		if ((junkfilter = estate->es_junkFilter) != NULL)
		{
			Datum		datum;
			bool		isNull;

			/*
			 * extract the 'ctid' junk attribute.
			 */
			if (operation == CMD_UPDATE || operation == CMD_DELETE)
			{
				if (!ExecGetJunkAttribute(junkfilter,
										  slot,
										  "ctid",
										  &datum,
										  &isNull))
					elog(ERROR, "could not find junk ctid column");

				/* shouldn't ever get a null result... */
				if (isNull)
					elog(ERROR, "ctid is NULL");

				tupleid = (ItemPointer) DatumGetPointer(datum);
				tuple_ctid = *tupleid;	/* make sure we don't free the ctid!! */
				tupleid = &tuple_ctid;
			}

			/*
			 * Process any FOR UPDATE or FOR SHARE locking requested.
			 */
			else if (estate->es_rowMarks != NIL)
			{
				ListCell   *l;

		lmark:	;
				foreach(l, estate->es_rowMarks)
				{
					ExecRowMark *erm = lfirst(l);
					HeapTupleData tuple;
					Buffer		buffer;
					ItemPointerData update_ctid;
					TransactionId update_xmax;
					TupleTableSlot *newSlot;
					LockTupleMode lockmode;
					HTSU_Result test;

					if (!ExecGetJunkAttribute(junkfilter,
											  slot,
											  erm->resname,
											  &datum,
											  &isNull))
						elog(ERROR, "could not find junk \"%s\" column",
							 erm->resname);

					/* shouldn't ever get a null result... */
					if (isNull)
						elog(ERROR, "\"%s\" is NULL", erm->resname);

					tuple.t_self = *((ItemPointer) DatumGetPointer(datum));

					if (erm->forUpdate)
						lockmode = LockTupleExclusive;
					else
						lockmode = LockTupleShared;

					test = heap_lock_tuple(erm->relation, &tuple, &buffer,
										   &update_ctid, &update_xmax,
										   estate->es_snapshot->curcid,
										   lockmode, erm->noWait);
					ReleaseBuffer(buffer);
					switch (test)
					{
						case HeapTupleSelfUpdated:
							/* treat it as deleted; do not process */
							goto lnext;

						case HeapTupleMayBeUpdated:
							break;

						case HeapTupleUpdated:
							if (IsXactIsoLevelSerializable)
								ereport(ERROR,
								 (errcode(ERRCODE_T_R_SERIALIZATION_FAILURE),
								  errmsg("could not serialize access due to concurrent update")));
							if (!ItemPointerEquals(&update_ctid,
												   &tuple.t_self))
							{
								/* updated, so look at updated version */
								newSlot = EvalPlanQual(estate,
													   erm->rti,
													   &update_ctid,
													   update_xmax,
												estate->es_snapshot->curcid);
								if (!TupIsNull(newSlot))
								{
									slot = planSlot = newSlot;
									estate->es_useEvalPlan = true;
									goto lmark;
								}
							}

							/*
							 * if tuple was deleted or PlanQual failed for
							 * updated tuple - we must not return this tuple!
							 */
							goto lnext;

						default:
							elog(ERROR, "unrecognized heap_lock_tuple status: %u",
								 test);
							return NULL;
					}
				}
			}

			/*
			 * Create a new "clean" tuple with all junk attributes removed. We
			 * don't need to do this for DELETE, however (there will in fact
			 * be no non-junk attributes in a DELETE!)
			 */
			if (operation != CMD_DELETE)
				slot = ExecFilterJunk(junkfilter, slot);
		}

		/*
		 * now that we have a tuple, do the appropriate thing with it.. either
		 * return it to the user, add it to a relation someplace, delete it
		 * from a relation, or modify some of its attributes.
		 */
		switch (operation)
		{
			case CMD_SELECT:
				ExecSelect(slot, dest, estate);
				result = slot;
				break;

			case CMD_INSERT:
				ExecInsert(slot, tupleid, planSlot, dest, estate);
				result = NULL;
				break;

			case CMD_DELETE:
				ExecDelete(tupleid, planSlot, dest, estate);
				result = NULL;
				break;

			case CMD_UPDATE:
				ExecUpdate(slot, tupleid, planSlot, dest, estate);
				result = NULL;
				break;

			default:
				elog(ERROR, "unrecognized operation code: %d",
					 (int) operation);
				result = NULL;
				break;
		}

		/*
		 * check our tuple count.. if we've processed the proper number then
		 * quit, else loop again and process more tuples.  Zero numberTuples
		 * means no limit.
		 */
		current_tuple_count++;
		if (numberTuples && numberTuples == current_tuple_count)
			break;
	}

	/*
	 * Process AFTER EACH STATEMENT triggers
	 */
	switch (operation)
	{
		case CMD_UPDATE:
			ExecASUpdateTriggers(estate, estate->es_result_relation_info);
			break;
		case CMD_DELETE:
			ExecASDeleteTriggers(estate, estate->es_result_relation_info);
			break;
		case CMD_INSERT:
			ExecASInsertTriggers(estate, estate->es_result_relation_info);
			break;
		default:
			/* do nothing */
			break;
	}

	/*
	 * here, result is either a slot containing a tuple in the case of a
	 * SELECT or NULL otherwise.
	 */
	return result;
}

/* ----------------------------------------------------------------
 *		ExecSelect
 *
 *		SELECTs are easy.. we just pass the tuple to the appropriate
 *		output function.
 * ----------------------------------------------------------------
 */
static void
ExecSelect(TupleTableSlot *slot,
		   DestReceiver *dest,
		   EState *estate)
{
	(*dest->receiveSlot) (slot, dest);
	IncrRetrieved();
	(estate->es_processed)++;
}

/* ----------------------------------------------------------------
 *		ExecInsert
 *
 *		INSERTs are trickier.. we have to insert the tuple into
 *		the base relation and insert appropriate tuples into the
 *		index relations.
 * ----------------------------------------------------------------
 */
static void
ExecInsert(TupleTableSlot *slot,
		   ItemPointer tupleid,
		   TupleTableSlot *planSlot,
		   DestReceiver *dest,
		   EState *estate)
{
	HeapTuple	tuple;
	ResultRelInfo *resultRelInfo;
	Relation	resultRelationDesc;
	Oid			newId;

	/*
	 * get the heap tuple out of the tuple table slot, making sure we have a
	 * writable copy
	 */
	tuple = ExecMaterializeSlot(slot);

	/*
	 * get information on the (current) result relation
	 */
	resultRelInfo = estate->es_result_relation_info;
	resultRelationDesc = resultRelInfo->ri_RelationDesc;

	/* BEFORE ROW INSERT Triggers */
	if (resultRelInfo->ri_TrigDesc &&
		resultRelInfo->ri_TrigDesc->n_before_row[TRIGGER_EVENT_INSERT] > 0)
	{
		HeapTuple	newtuple;

		newtuple = ExecBRInsertTriggers(estate, resultRelInfo, tuple);

		if (newtuple == NULL)	/* "do nothing" */
			return;

		if (newtuple != tuple)	/* modified by Trigger(s) */
		{
			/*
			 * Put the modified tuple into a slot for convenience of routines
			 * below.  We assume the tuple was allocated in per-tuple memory
			 * context, and therefore will go away by itself. The tuple table
			 * slot should not try to clear it.
			 */
			TupleTableSlot *newslot = estate->es_trig_tuple_slot;

			if (newslot->tts_tupleDescriptor != slot->tts_tupleDescriptor)
				ExecSetSlotDescriptor(newslot, slot->tts_tupleDescriptor);
			ExecStoreTuple(newtuple, newslot, InvalidBuffer, false);
			slot = newslot;
			tuple = newtuple;
		}
	}

	/*
	 * Check the constraints of the tuple
	 */
	if (resultRelationDesc->rd_att->constr)
		ExecConstraints(resultRelInfo, slot, estate);

	/*
	 * insert the tuple
	 *
	 * Note: heap_insert returns the tid (location) of the new tuple in the
	 * t_self field.
	 */
	newId = heap_insert(resultRelationDesc, tuple,
						estate->es_snapshot->curcid,
						true, true);

	IncrAppended();
	(estate->es_processed)++;
	estate->es_lastoid = newId;
	setLastTid(&(tuple->t_self));

	/*
	 * insert index entries for tuple
	 */
	if (resultRelInfo->ri_NumIndices > 0)
		ExecInsertIndexTuples(slot, &(tuple->t_self), estate, false, false);

	/* AFTER ROW INSERT Triggers */
	ExecARInsertTriggers(estate, resultRelInfo, tuple);

	/* Process RETURNING if present */
	if (resultRelInfo->ri_projectReturning)
		ExecProcessReturning(resultRelInfo->ri_projectReturning,
							 slot, planSlot, dest);
}

/* ----------------------------------------------------------------
 *		ExecDelete
 *
 *		DELETE is like UPDATE, except that we delete the tuple and no
 *		index modifications are needed
 * ----------------------------------------------------------------
 */
static void
ExecDelete(ItemPointer tupleid,
		   TupleTableSlot *planSlot,
		   DestReceiver *dest,
		   EState *estate)
{
	ResultRelInfo *resultRelInfo;
	Relation	resultRelationDesc;
	HTSU_Result result;
	ItemPointerData update_ctid;
	TransactionId update_xmax;
	
	/*
	 * get information on the (current) result relation
	 */
	resultRelInfo = estate->es_result_relation_info;
	resultRelationDesc = resultRelInfo->ri_RelationDesc;

	/* BEFORE ROW DELETE Triggers */
	if (resultRelInfo->ri_TrigDesc &&
		resultRelInfo->ri_TrigDesc->n_before_row[TRIGGER_EVENT_DELETE] > 0)
	{
		bool		dodelete;

		dodelete = ExecBRDeleteTriggers(estate, resultRelInfo, tupleid,
										estate->es_snapshot->curcid);

		if (!dodelete)			/* "do nothing" */
			return;
	}
	
	/*
	 * delete the tuple
	 *
	 * Note: if es_crosscheck_snapshot isn't InvalidSnapshot, we check that
	 * the row to be deleted is visible to that snapshot, and throw a can't-
	 * serialize error if not.	This is a special-case behavior needed for
	 * referential integrity updates in serializable transactions.
	 */
ldelete:;
	result = heap_delete(resultRelationDesc, tupleid,
						 &update_ctid, &update_xmax,
						 estate->es_snapshot->curcid,
						 estate->es_crosscheck_snapshot,
						 true /* wait for commit */ );
	switch (result)
	{
		case HeapTupleSelfUpdated:
			/* already deleted by self; nothing to do */
			return;

		case HeapTupleMayBeUpdated:
			break;

		case HeapTupleUpdated:
			if (IsXactIsoLevelSerializable)
				ereport(ERROR,
						(errcode(ERRCODE_T_R_SERIALIZATION_FAILURE),
						 errmsg("could not serialize access due to concurrent update")));
			else if (!ItemPointerEquals(tupleid, &update_ctid))
			{
				TupleTableSlot *epqslot;

				epqslot = EvalPlanQual(estate,
									   resultRelInfo->ri_RangeTableIndex,
									   &update_ctid,
									   update_xmax,
									   estate->es_snapshot->curcid);
				if (!TupIsNull(epqslot))
				{
					*tupleid = update_ctid;
					goto ldelete;
				}
			}
			/* tuple already deleted; nothing to do */
			return;

		default:
			elog(ERROR, "unrecognized heap_delete status: %u", result);
			return;
	}

	IncrDeleted();
	(estate->es_processed)++;

	/*
	 * Note: Normally one would think that we have to delete index tuples
	 * associated with the heap tuple now...
	 *
	 * ... but in POSTGRES, we have no need to do this because VACUUM will
	 * take care of it later.  We can't delete index tuples immediately
	 * anyway, since the tuple is still visible to other transactions.
	 */

	/* AFTER ROW DELETE Triggers */
	ExecARDeleteTriggers(estate, resultRelInfo, tupleid);

	/*
	 * Send TxCache invalidation if serializable, or process RETURNING
	 * if present. We lump these together because they both need to
	 * fetch the tuple.
	 */
	if (IsXactIsoLevelSerializable || resultRelInfo->ri_projectReturning)
	{
		/*
		 * We have to put the target tuple into a slot, which means first we
		 * gotta fetch it.	We can use the trigger tuple slot.
		 */
		TupleTableSlot *slot = estate->es_trig_tuple_slot;
		HeapTupleData deltuple;
		Buffer		delbuffer;

		deltuple.t_self = *tupleid;
		if (!heap_fetch(resultRelationDesc, SnapshotAny,
						&deltuple, &delbuffer, false, NULL))
			elog(ERROR, "failed to fetch deleted tuple for DELETE RETURNING");

		if (slot->tts_tupleDescriptor != RelationGetDescr(resultRelationDesc))
			ExecSetSlotDescriptor(slot, RelationGetDescr(resultRelationDesc));
		ExecStoreTuple(&deltuple, slot, InvalidBuffer, false);


		if (IsXactIsoLevelSerializable)
			ExecInsertIndexTuples(slot, tupleid, estate, false, true);

		if (resultRelInfo->ri_projectReturning)
			ExecProcessReturning(resultRelInfo->ri_projectReturning,
								 slot, planSlot, dest);

		ExecClearTuple(slot);
		ReleaseBuffer(delbuffer);
	}
}

/* ----------------------------------------------------------------
 *		ExecUpdate
 *
 *		note: we can't run UPDATE queries with transactions
 *		off because UPDATEs are actually INSERTs and our
 *		scan will mistakenly loop forever, updating the tuple
 *		it just inserted..	This should be fixed but until it
 *		is, we don't want to get stuck in an infinite loop
 *		which corrupts your database..
 * ----------------------------------------------------------------
 */
static void
ExecUpdate(TupleTableSlot *slot,
		   ItemPointer tupleid,
		   TupleTableSlot *planSlot,
		   DestReceiver *dest,
		   EState *estate)
{
	HeapTuple	tuple;
	ResultRelInfo *resultRelInfo;
	Relation	resultRelationDesc;
	HTSU_Result result;
	ItemPointerData update_ctid;
	TransactionId update_xmax;

	/*
	 * abort the operation if not running transactions
	 */
	if (IsBootstrapProcessingMode())
		elog(ERROR, "cannot UPDATE during bootstrap");

	/*
	 * get the heap tuple out of the tuple table slot, making sure we have a
	 * writable copy
	 */
	tuple = ExecMaterializeSlot(slot);

	/*
	 * get information on the (current) result relation
	 */
	resultRelInfo = estate->es_result_relation_info;
	resultRelationDesc = resultRelInfo->ri_RelationDesc;

	/* BEFORE ROW UPDATE Triggers */
	if (resultRelInfo->ri_TrigDesc &&
		resultRelInfo->ri_TrigDesc->n_before_row[TRIGGER_EVENT_UPDATE] > 0)
	{
		HeapTuple	newtuple;

		newtuple = ExecBRUpdateTriggers(estate, resultRelInfo,
										tupleid, tuple,
										estate->es_snapshot->curcid);

		if (newtuple == NULL)	/* "do nothing" */
			return;

		if (newtuple != tuple)	/* modified by Trigger(s) */
		{
			/*
			 * Put the modified tuple into a slot for convenience of routines
			 * below.  We assume the tuple was allocated in per-tuple memory
			 * context, and therefore will go away by itself. The tuple table
			 * slot should not try to clear it.
			 */
			TupleTableSlot *newslot = estate->es_trig_tuple_slot;

			if (newslot->tts_tupleDescriptor != slot->tts_tupleDescriptor)
				ExecSetSlotDescriptor(newslot, slot->tts_tupleDescriptor);
			ExecStoreTuple(newtuple, newslot, InvalidBuffer, false);
			slot = newslot;
			tuple = newtuple;
		}
	}

	/*
	 * Check the constraints of the tuple
	 *
	 * If we generate a new candidate tuple after EvalPlanQual testing, we
	 * must loop back here and recheck constraints.  (We don't need to redo
	 * triggers, however.  If there are any BEFORE triggers then trigger.c
	 * will have done heap_lock_tuple to lock the correct tuple, so there's no
	 * need to do them again.)
	 */
lreplace:;
	if (resultRelationDesc->rd_att->constr)
		ExecConstraints(resultRelInfo, slot, estate);

	/*
	 * replace the heap tuple
	 *
	 * Note: if es_crosscheck_snapshot isn't InvalidSnapshot, we check that
	 * the row to be updated is visible to that snapshot, and throw a can't-
	 * serialize error if not.	This is a special-case behavior needed for
	 * referential integrity updates in serializable transactions.
	 */
	result = heap_update(resultRelationDesc, tupleid, tuple,
						 &update_ctid, &update_xmax,
						 estate->es_snapshot->curcid,
						 estate->es_crosscheck_snapshot,
						 true /* wait for commit */ );
	switch (result)
	{
		case HeapTupleSelfUpdated:
			/* already deleted by self; nothing to do */
			return;

		case HeapTupleMayBeUpdated:
			break;

		case HeapTupleUpdated:
			if (IsXactIsoLevelSerializable)
				ereport(ERROR,
						(errcode(ERRCODE_T_R_SERIALIZATION_FAILURE),
						 errmsg("could not serialize access due to concurrent update")));
			else if (!ItemPointerEquals(tupleid, &update_ctid))
			{
				TupleTableSlot *epqslot;

				epqslot = EvalPlanQual(estate,
									   resultRelInfo->ri_RangeTableIndex,
									   &update_ctid,
									   update_xmax,
									   estate->es_snapshot->curcid);
				if (!TupIsNull(epqslot))
				{
					*tupleid = update_ctid;
					slot = ExecFilterJunk(estate->es_junkFilter, epqslot);
					tuple = ExecMaterializeSlot(slot);
					goto lreplace;
				}
			}
			/* tuple already deleted; nothing to do */
			return;

		default:
			elog(ERROR, "unrecognized heap_update status: %u", result);
			return;
	}

	IncrReplaced();
	(estate->es_processed)++;

	/*
	 * Note: instead of having to update the old index tuples associated with
	 * the heap tuple, all we do is form and insert new index tuples. This is
	 * because UPDATEs are actually DELETEs and INSERTs, and index tuple
	 * deletion is done later by VACUUM (see notes in ExecDelete).	All we do
	 * here is insert new index tuples.  -cim 9/27/89
	 */

	/*
	 * insert index entries for tuple
	 *
	 * Note: heap_update returns the tid (location) of the new tuple in the
	 * t_self field.
	 */
	if (resultRelInfo->ri_NumIndices > 0)
		ExecInsertIndexTuples(slot, &(tuple->t_self), estate, false, false);

	/* AFTER ROW UPDATE Triggers */
	ExecARUpdateTriggers(estate, resultRelInfo, tupleid, tuple);

	/* Process RETURNING if present */
	if (resultRelInfo->ri_projectReturning)
		ExecProcessReturning(resultRelInfo->ri_projectReturning,
							 slot, planSlot, dest);
}

/*
 * ExecRelCheck --- check that tuple meets constraints for result relation
 */
static const char *
ExecRelCheck(ResultRelInfo *resultRelInfo,
			 TupleTableSlot *slot, EState *estate)
{
	Relation	rel = resultRelInfo->ri_RelationDesc;
	int			ncheck = rel->rd_att->constr->num_check;
	ConstrCheck *check = rel->rd_att->constr->check;
	ExprContext *econtext;
	MemoryContext oldContext;
	List	   *qual;
	int			i;

	/*
	 * If first time through for this result relation, build expression
	 * nodetrees for rel's constraint expressions.  Keep them in the per-query
	 * memory context so they'll survive throughout the query.
	 */
	if (resultRelInfo->ri_ConstraintExprs == NULL)
	{
		oldContext = MemoryContextSwitchTo(estate->es_query_cxt);
		resultRelInfo->ri_ConstraintExprs =
			(List **) palloc(ncheck * sizeof(List *));
		for (i = 0; i < ncheck; i++)
		{
			/* ExecQual wants implicit-AND form */
			qual = make_ands_implicit(stringToNode(check[i].ccbin));
			resultRelInfo->ri_ConstraintExprs[i] = (List *)
				ExecPrepareExpr((Expr *) qual, estate);
		}
		MemoryContextSwitchTo(oldContext);
	}

	/*
	 * We will use the EState's per-tuple context for evaluating constraint
	 * expressions (creating it if it's not already there).
	 */
	econtext = GetPerTupleExprContext(estate);

	/* Arrange for econtext's scan tuple to be the tuple under test */
	econtext->ecxt_scantuple = slot;

	/* And evaluate the constraints */
	for (i = 0; i < ncheck; i++)
	{
		qual = resultRelInfo->ri_ConstraintExprs[i];

		/*
		 * NOTE: SQL92 specifies that a NULL result from a constraint
		 * expression is not to be treated as a failure.  Therefore, tell
		 * ExecQual to return TRUE for NULL.
		 */
		if (!ExecQual(qual, econtext, true))
			return check[i].ccname;
	}

	/* NULL result means no error */
	return NULL;
}

void
ExecConstraints(ResultRelInfo *resultRelInfo,
				TupleTableSlot *slot, EState *estate)
{
	Relation	rel = resultRelInfo->ri_RelationDesc;
	TupleConstr *constr = rel->rd_att->constr;

	Assert(constr);

	if (constr->has_not_null)
	{
		int			natts = rel->rd_att->natts;
		int			attrChk;

		for (attrChk = 1; attrChk <= natts; attrChk++)
		{
			if (rel->rd_att->attrs[attrChk - 1]->attnotnull &&
				slot_attisnull(slot, attrChk))
				ereport(ERROR,
						(errcode(ERRCODE_NOT_NULL_VIOLATION),
						 errmsg("null value in column \"%s\" violates not-null constraint",
						NameStr(rel->rd_att->attrs[attrChk - 1]->attname))));
		}
	}

	if (constr->num_check > 0)
	{
		const char *failed;

		if ((failed = ExecRelCheck(resultRelInfo, slot, estate)) != NULL)
			ereport(ERROR,
					(errcode(ERRCODE_CHECK_VIOLATION),
					 errmsg("new row for relation \"%s\" violates check constraint \"%s\"",
							RelationGetRelationName(rel), failed)));
	}
}

/*
 * ExecProcessReturning --- evaluate a RETURNING list and send to dest
 *
 * projectReturning: RETURNING projection info for current result rel
 * tupleSlot: slot holding tuple actually inserted/updated/deleted
 * planSlot: slot holding tuple returned by top plan node
 * dest: where to send the output
 */
static void
ExecProcessReturning(ProjectionInfo *projectReturning,
					 TupleTableSlot *tupleSlot,
					 TupleTableSlot *planSlot,
					 DestReceiver *dest)
{
	ExprContext *econtext = projectReturning->pi_exprContext;
	TupleTableSlot *retSlot;

	/*
	 * Reset per-tuple memory context to free any expression evaluation
	 * storage allocated in the previous cycle.
	 */
	ResetExprContext(econtext);

	/* Make tuple and any needed join variables available to ExecProject */
	econtext->ecxt_scantuple = tupleSlot;
	econtext->ecxt_outertuple = planSlot;

	/* Compute the RETURNING expressions */
	retSlot = ExecProject(projectReturning, NULL);

	/* Send to dest */
	(*dest->receiveSlot) (retSlot, dest);

	ExecClearTuple(retSlot);
}

/*
 * Check a modified tuple to see if we want to process its updated version
 * under READ COMMITTED rules.
 *
 * See backend/executor/README for some info about how this works.
 *
 *	estate - executor state data
 *	rti - rangetable index of table containing tuple
 *	*tid - t_ctid from the outdated tuple (ie, next updated version)
 *	priorXmax - t_xmax from the outdated tuple
 *	curCid - command ID of current command of my transaction
 *
 * *tid is also an output parameter: it's modified to hold the TID of the
 * latest version of the tuple (note this may be changed even on failure)
 *
 * Returns a slot containing the new candidate update/delete tuple, or
 * NULL if we determine we shouldn't process the row.
 */
TupleTableSlot *
EvalPlanQual(EState *estate, Index rti,
			 ItemPointer tid, TransactionId priorXmax, CommandId curCid)
{
	evalPlanQual *epq;
	EState	   *epqstate;
	Relation	relation;
	HeapTupleData tuple;
	HeapTuple	copyTuple = NULL;
	bool		endNode;

	Assert(rti != 0);

	/*
	 * find relation containing target tuple
	 */
	if (estate->es_result_relation_info != NULL &&
		estate->es_result_relation_info->ri_RangeTableIndex == rti)
		relation = estate->es_result_relation_info->ri_RelationDesc;
	else
	{
		ListCell   *l;

		relation = NULL;
		foreach(l, estate->es_rowMarks)
		{
			if (((ExecRowMark *) lfirst(l))->rti == rti)
			{
				relation = ((ExecRowMark *) lfirst(l))->relation;
				break;
			}
		}
		if (relation == NULL)
			elog(ERROR, "could not find RowMark for RT index %u", rti);
	}

	/*
	 * fetch tid tuple
	 *
	 * Loop here to deal with updated or busy tuples
	 */
	tuple.t_self = *tid;
	for (;;)
	{
		Buffer		buffer;

		if (heap_fetch(relation, SnapshotDirty, &tuple, &buffer, true, NULL))
		{
			/*
			 * If xmin isn't what we're expecting, the slot must have been
			 * recycled and reused for an unrelated tuple.	This implies that
			 * the latest version of the row was deleted, so we need do
			 * nothing.  (Should be safe to examine xmin without getting
			 * buffer's content lock, since xmin never changes in an existing
			 * tuple.)
			 */
			if (!TransactionIdEquals(HeapTupleHeaderGetXmin(tuple.t_data),
									 priorXmax))
			{
				ReleaseBuffer(buffer);
				return NULL;
			}

			/* otherwise xmin should not be dirty... */
			if (TransactionIdIsValid(SnapshotDirty->xmin))
				elog(ERROR, "t_xmin is uncommitted in tuple to be updated");

			/*
			 * If tuple is being updated by other transaction then we have to
			 * wait for its commit/abort.
			 */
			if (TransactionIdIsValid(SnapshotDirty->xmax))
			{
				ReleaseBuffer(buffer);
				XactLockTableWait(SnapshotDirty->xmax);
				continue;		/* loop back to repeat heap_fetch */
			}

			/*
			 * If tuple was inserted by our own transaction, we have to check
			 * cmin against curCid: cmin >= curCid means our command cannot
			 * see the tuple, so we should ignore it.  Without this we are
			 * open to the "Halloween problem" of indefinitely re-updating the
			 * same tuple.	(We need not check cmax because
			 * HeapTupleSatisfiesDirty will consider a tuple deleted by our
			 * transaction dead, regardless of cmax.)  We just checked that
			 * priorXmax == xmin, so we can test that variable instead of
			 * doing HeapTupleHeaderGetXmin again.
			 */
			if (TransactionIdIsCurrentTransactionId(priorXmax) &&
				HeapTupleHeaderGetCmin(tuple.t_data) >= curCid)
			{
				ReleaseBuffer(buffer);
				return NULL;
			}

			/*
			 * We got tuple - now copy it for use by recheck query.
			 */
			copyTuple = heap_copytuple(&tuple);
			ReleaseBuffer(buffer);
			break;
		}

		/*
		 * If the referenced slot was actually empty, the latest version of
		 * the row must have been deleted, so we need do nothing.
		 */
		if (tuple.t_data == NULL)
		{
			ReleaseBuffer(buffer);
			return NULL;
		}

		/*
		 * As above, if xmin isn't what we're expecting, do nothing.
		 */
		if (!TransactionIdEquals(HeapTupleHeaderGetXmin(tuple.t_data),
								 priorXmax))
		{
			ReleaseBuffer(buffer);
			return NULL;
		}

		/*
		 * If we get here, the tuple was found but failed SnapshotDirty.
		 * Assuming the xmin is either a committed xact or our own xact (as it
		 * certainly should be if we're trying to modify the tuple), this must
		 * mean that the row was updated or deleted by either a committed xact
		 * or our own xact.  If it was deleted, we can ignore it; if it was
		 * updated then chain up to the next version and repeat the whole
		 * test.
		 *
		 * As above, it should be safe to examine xmax and t_ctid without the
		 * buffer content lock, because they can't be changing.
		 */
		if (ItemPointerEquals(&tuple.t_self, &tuple.t_data->t_ctid))
		{
			/* deleted, so forget about it */
			ReleaseBuffer(buffer);
			return NULL;
		}

		/* updated, so look at the updated row */
		tuple.t_self = tuple.t_data->t_ctid;
		/* updated row should have xmin matching this xmax */
		priorXmax = HeapTupleHeaderGetXmax(tuple.t_data);
		ReleaseBuffer(buffer);
		/* loop back to fetch next in chain */
	}

	/*
	 * For UPDATE/DELETE we have to return tid of actual row we're executing
	 * PQ for.
	 */
	*tid = tuple.t_self;

	/*
	 * Need to run a recheck subquery.	Find or create a PQ stack entry.
	 */
	epq = estate->es_evalPlanQual;
	endNode = true;

	if (epq != NULL && epq->rti == 0)
	{
		/* Top PQ stack entry is idle, so re-use it */
		Assert(!(estate->es_useEvalPlan) && epq->next == NULL);
		epq->rti = rti;
		endNode = false;
	}

	/*
	 * If this is request for another RTE - Ra, - then we have to check wasn't
	 * PlanQual requested for Ra already and if so then Ra' row was updated
	 * again and we have to re-start old execution for Ra and forget all what
	 * we done after Ra was suspended. Cool? -:))
	 */
	if (epq != NULL && epq->rti != rti &&
		epq->estate->es_evTuple[rti - 1] != NULL)
	{
		do
		{
			evalPlanQual *oldepq;

			/* stop execution */
			EvalPlanQualStop(epq);
			/* pop previous PlanQual from the stack */
			oldepq = epq->next;
			Assert(oldepq && oldepq->rti != 0);
			/* push current PQ to freePQ stack */
			oldepq->free = epq;
			epq = oldepq;
			estate->es_evalPlanQual = epq;
		} while (epq->rti != rti);
	}

	/*
	 * If we are requested for another RTE then we have to suspend execution
	 * of current PlanQual and start execution for new one.
	 */
	if (epq == NULL || epq->rti != rti)
	{
		/* try to reuse plan used previously */
		evalPlanQual *newepq = (epq != NULL) ? epq->free : NULL;

		if (newepq == NULL)		/* first call or freePQ stack is empty */
		{
			newepq = (evalPlanQual *) palloc0(sizeof(evalPlanQual));
			newepq->free = NULL;
			newepq->estate = NULL;
			newepq->planstate = NULL;
		}
		else
		{
			/* recycle previously used PlanQual */
			Assert(newepq->estate == NULL);
			epq->free = NULL;
		}
		/* push current PQ to the stack */
		newepq->next = epq;
		epq = newepq;
		estate->es_evalPlanQual = epq;
		epq->rti = rti;
		endNode = false;
	}

	Assert(epq->rti == rti);

	/*
	 * Ok - we're requested for the same RTE.  Unfortunately we still have to
	 * end and restart execution of the plan, because ExecReScan wouldn't
	 * ensure that upper plan nodes would reset themselves.  We could make
	 * that work if insertion of the target tuple were integrated with the
	 * Param mechanism somehow, so that the upper plan nodes know that their
	 * children's outputs have changed.
	 *
	 * Note that the stack of free evalPlanQual nodes is quite useless at the
	 * moment, since it only saves us from pallocing/releasing the
	 * evalPlanQual nodes themselves.  But it will be useful once we implement
	 * ReScan instead of end/restart for re-using PlanQual nodes.
	 */
	if (endNode)
	{
		/* stop execution */
		EvalPlanQualStop(epq);
	}

	/*
	 * Initialize new recheck query.
	 *
	 * Note: if we were re-using PlanQual plans via ExecReScan, we'd need to
	 * instead copy down changeable state from the top plan (including
	 * es_result_relation_info, es_junkFilter) and reset locally changeable
	 * state in the epq (including es_param_exec_vals, es_evTupleNull).
	 */
	EvalPlanQualStart(epq, estate, epq->next);

	/*
	 * free old RTE' tuple, if any, and store target tuple where relation's
	 * scan node will see it
	 */
	epqstate = epq->estate;
	if (epqstate->es_evTuple[rti - 1] != NULL)
		heap_freetuple(epqstate->es_evTuple[rti - 1]);
	epqstate->es_evTuple[rti - 1] = copyTuple;

	return EvalPlanQualNext(estate);
}

static TupleTableSlot *
EvalPlanQualNext(EState *estate)
{
	evalPlanQual *epq = estate->es_evalPlanQual;
	MemoryContext oldcontext;
	TupleTableSlot *slot;

	Assert(epq->rti != 0);

lpqnext:;
	oldcontext = MemoryContextSwitchTo(epq->estate->es_query_cxt);
	slot = ExecProcNode(epq->planstate);
	MemoryContextSwitchTo(oldcontext);

	/*
	 * No more tuples for this PQ. Continue previous one.
	 */
	if (TupIsNull(slot))
	{
		evalPlanQual *oldepq;

		/* stop execution */
		EvalPlanQualStop(epq);
		/* pop old PQ from the stack */
		oldepq = epq->next;
		if (oldepq == NULL)
		{
			/* this is the first (oldest) PQ - mark as free */
			epq->rti = 0;
			estate->es_useEvalPlan = false;
			/* and continue Query execution */
			return NULL;
		}
		Assert(oldepq->rti != 0);
		/* push current PQ to freePQ stack */
		oldepq->free = epq;
		epq = oldepq;
		estate->es_evalPlanQual = epq;
		goto lpqnext;
	}

	return slot;
}

static void
EndEvalPlanQual(EState *estate)
{
	evalPlanQual *epq = estate->es_evalPlanQual;

	if (epq->rti == 0)			/* plans already shutdowned */
	{
		Assert(epq->next == NULL);
		return;
	}

	for (;;)
	{
		evalPlanQual *oldepq;

		/* stop execution */
		EvalPlanQualStop(epq);
		/* pop old PQ from the stack */
		oldepq = epq->next;
		if (oldepq == NULL)
		{
			/* this is the first (oldest) PQ - mark as free */
			epq->rti = 0;
			estate->es_useEvalPlan = false;
			break;
		}
		Assert(oldepq->rti != 0);
		/* push current PQ to freePQ stack */
		oldepq->free = epq;
		epq = oldepq;
		estate->es_evalPlanQual = epq;
	}
}

/*
 * Start execution of one level of PlanQual.
 *
 * This is a cut-down version of ExecutorStart(): we copy some state from
 * the top-level estate rather than initializing it fresh.
 */
static void
EvalPlanQualStart(evalPlanQual *epq, EState *estate, evalPlanQual *priorepq)
{
	EState	   *epqstate;
	int			rtsize;
	MemoryContext oldcontext;

	rtsize = list_length(estate->es_range_table);

	/*
	 * It's tempting to think about using CreateSubExecutorState here, but
	 * at present we can't because of memory leakage concerns ...
	 */
	epq->estate = epqstate = CreateExecutorState();

	oldcontext = MemoryContextSwitchTo(epqstate->es_query_cxt);

	/*
	 * The epqstates share the top query's copy of unchanging state such as
	 * the snapshot, rangetable, result-rel info, and external Param info.
	 * They need their own copies of local state, including a tuple table,
	 * es_param_exec_vals, etc.
	 */
	epqstate->es_direction = ForwardScanDirection;
	epqstate->es_snapshot = estate->es_snapshot;
	epqstate->es_crosscheck_snapshot = estate->es_crosscheck_snapshot;
	epqstate->es_range_table = estate->es_range_table;
	epqstate->es_result_relations = estate->es_result_relations;
	epqstate->es_num_result_relations = estate->es_num_result_relations;
	epqstate->es_result_relation_info = estate->es_result_relation_info;
	epqstate->es_junkFilter = estate->es_junkFilter;
	epqstate->es_into_relation_descriptor = estate->es_into_relation_descriptor;
	epqstate->es_into_relation_use_wal = estate->es_into_relation_use_wal;
	epqstate->es_param_list_info = estate->es_param_list_info;
	if (estate->es_topPlan->nParamExec > 0)
		epqstate->es_param_exec_vals = (ParamExecData *)
			palloc0(estate->es_topPlan->nParamExec * sizeof(ParamExecData));
	epqstate->es_rowMarks = estate->es_rowMarks;
	epqstate->es_instrument = estate->es_instrument;
	epqstate->es_select_into = estate->es_select_into;
	epqstate->es_into_oids = estate->es_into_oids;
	epqstate->es_topPlan = estate->es_topPlan;

	/*
	 * Each epqstate must have its own es_evTupleNull state, but all the stack
	 * entries share es_evTuple state.	This allows sub-rechecks to inherit
	 * the value being examined by an outer recheck.
	 */
	epqstate->es_evTupleNull = (bool *) palloc0(rtsize * sizeof(bool));
	if (priorepq == NULL)
		/* first PQ stack entry */
		epqstate->es_evTuple = (HeapTuple *)
			palloc0(rtsize * sizeof(HeapTuple));
	else
		/* later stack entries share the same storage */
		epqstate->es_evTuple = priorepq->estate->es_evTuple;

	epqstate->es_tupleTable =
		ExecCreateTupleTable(estate->es_tupleTable->size);

	epq->planstate = ExecInitNode(estate->es_topPlan, epqstate, 0);

	MemoryContextSwitchTo(oldcontext);
}

/*
 * End execution of one level of PlanQual.
 *
 * This is a cut-down version of ExecutorEnd(); basically we want to do most
 * of the normal cleanup, but *not* close result relations (which we are
 * just sharing from the outer query).
 */
static void
EvalPlanQualStop(evalPlanQual *epq)
{
	EState	   *epqstate = epq->estate;
	MemoryContext oldcontext;

	oldcontext = MemoryContextSwitchTo(epqstate->es_query_cxt);

	ExecEndNode(epq->planstate);

	ExecDropTupleTable(epqstate->es_tupleTable, true);
	epqstate->es_tupleTable = NULL;

	if (epqstate->es_evTuple[epq->rti - 1] != NULL)
	{
		heap_freetuple(epqstate->es_evTuple[epq->rti - 1]);
		epqstate->es_evTuple[epq->rti - 1] = NULL;
	}

	MemoryContextSwitchTo(oldcontext);

	FreeExecutorState(epqstate);

	epq->estate = NULL;
	epq->planstate = NULL;
}


/*
 * Support for SELECT INTO (a/k/a CREATE TABLE AS)
 *
 * We implement SELECT INTO by diverting SELECT's normal output with
 * a specialized DestReceiver type.
 *
 * TODO: remove some of the INTO-specific cruft from EState, and keep
 * it in the DestReceiver instead.
 */

typedef struct
{
	DestReceiver pub;			/* publicly-known function pointers */
	EState	   *estate;			/* EState we are working with */
} DR_intorel;

/*
 * OpenIntoRel --- actually create the SELECT INTO target relation
 *
 * This also replaces QueryDesc->dest with the special DestReceiver for
 * SELECT INTO.  We assume that the correct result tuple type has already
 * been placed in queryDesc->tupDesc.
 */
static void
OpenIntoRel(QueryDesc *queryDesc)
{
	Query	   *parseTree = queryDesc->parsetree;
	EState	   *estate = queryDesc->estate;
	Relation	intoRelationDesc;
	char	   *intoName;
	Oid			namespaceId;
	Oid			tablespaceId;
	Datum		reloptions;
	AclResult	aclresult;
	Oid			intoRelationId;
	TupleDesc	tupdesc;
	DR_intorel *myState;

	/*
	 * Check consistency of arguments
	 */
	if (parseTree->intoOnCommit != ONCOMMIT_NOOP && !parseTree->into->istemp)
		ereport(ERROR,
				(errcode(ERRCODE_INVALID_TABLE_DEFINITION),
				 errmsg("ON COMMIT can only be used on temporary tables")));

	/*
	 * Find namespace to create in, check its permissions
	 */
	intoName = parseTree->into->relname;
	namespaceId = RangeVarGetCreationNamespace(parseTree->into);

	aclresult = pg_namespace_aclcheck(namespaceId, GetUserId(),
									  ACL_CREATE);
	if (aclresult != ACLCHECK_OK)
		aclcheck_error(aclresult, ACL_KIND_NAMESPACE,
					   get_namespace_name(namespaceId));

	/*
	 * Select tablespace to use.  If not specified, use default_tablespace
	 * (which may in turn default to database's default).
	 */
	if (parseTree->intoTableSpaceName)
	{
		tablespaceId = get_tablespace_oid(parseTree->intoTableSpaceName);
		if (!OidIsValid(tablespaceId))
			ereport(ERROR,
					(errcode(ERRCODE_UNDEFINED_OBJECT),
					 errmsg("tablespace \"%s\" does not exist",
							parseTree->intoTableSpaceName)));
	}
	else
	{
		tablespaceId = GetDefaultTablespace();
		/* note InvalidOid is OK in this case */
	}

	/* Check permissions except when using the database's default space */
	if (OidIsValid(tablespaceId))
	{
		AclResult	aclresult;

		aclresult = pg_tablespace_aclcheck(tablespaceId, GetUserId(),
										   ACL_CREATE);

		if (aclresult != ACLCHECK_OK)
			aclcheck_error(aclresult, ACL_KIND_TABLESPACE,
						   get_tablespace_name(tablespaceId));
	}

	/* Parse and validate any reloptions */
	reloptions = transformRelOptions((Datum) 0,
									 parseTree->intoOptions,
									 true,
									 false);
	(void) heap_reloptions(RELKIND_RELATION, reloptions, true);

	/* have to copy the actual tupdesc to get rid of any constraints */
	tupdesc = CreateTupleDescCopy(queryDesc->tupDesc);

	/* Now we can actually create the new relation */
	intoRelationId = heap_create_with_catalog(intoName,
											  namespaceId,
											  tablespaceId,
											  InvalidOid,
											  GetUserId(),
											  tupdesc,
											  RELKIND_RELATION,
											  false,
											  true,
											  0,
											  parseTree->intoOnCommit,
											  reloptions,
											  allowSystemTableMods);

	FreeTupleDesc(tupdesc);

	/*
	 * Advance command counter so that the newly-created relation's catalog
	 * tuples will be visible to heap_open.
	 */
	CommandCounterIncrement();

	/*
	 * If necessary, create a TOAST table for the INTO relation. Note that
	 * AlterTableCreateToastTable ends with CommandCounterIncrement(), so that
	 * the TOAST table will be visible for insertion.
	 */
	AlterTableCreateToastTable(intoRelationId);

	/*
	 * And open the constructed table for writing.
	 */
	intoRelationDesc = heap_open(intoRelationId, AccessExclusiveLock);

	/* use_wal off requires rd_targblock be initially invalid */
	Assert(intoRelationDesc->rd_targblock == InvalidBlockNumber);

	/*
	 * We can skip WAL-logging the insertions, unless PITR is in use.
	 *
	 * Note that for a non-temp INTO table, this is safe only because we know
	 * that the catalog changes above will have been WAL-logged, and so
	 * RecordTransactionCommit will think it needs to WAL-log the eventual
	 * transaction commit.	Else the commit might be lost, even though all the
	 * data is safely fsync'd ...
	 */
	estate->es_into_relation_use_wal = XLogArchivingActive();
	estate->es_into_relation_descriptor = intoRelationDesc;

	/*
	 * Now replace the query's DestReceiver with one for SELECT INTO
	 */
	queryDesc->dest = CreateDestReceiver(DestIntoRel, NULL);
	myState = (DR_intorel *) queryDesc->dest;
	Assert(myState->pub.mydest == DestIntoRel);
	myState->estate = estate;
}

/*
 * CloseIntoRel --- clean up SELECT INTO at ExecutorEnd time
 */
static void
CloseIntoRel(QueryDesc *queryDesc)
{
	EState	   *estate = queryDesc->estate;

	/* OpenIntoRel might never have gotten called */
	if (estate->es_into_relation_descriptor)
	{
		/*
		 * If we skipped using WAL, and it's not a temp relation, we must
		 * force the relation down to disk before it's safe to commit the
		 * transaction.  This requires forcing out any dirty buffers and then
		 * doing a forced fsync.
		 */
		if (!estate->es_into_relation_use_wal &&
			!estate->es_into_relation_descriptor->rd_istemp)
		{
			FlushRelationBuffers(estate->es_into_relation_descriptor);
			/* FlushRelationBuffers will have opened rd_smgr */
			smgrimmedsync(estate->es_into_relation_descriptor->rd_smgr);
		}

		/* close rel, but keep lock until commit */
		heap_close(estate->es_into_relation_descriptor, NoLock);

		estate->es_into_relation_descriptor = NULL;
	}
}

/*
 * CreateIntoRelDestReceiver -- create a suitable DestReceiver object
 *
 * Since CreateDestReceiver doesn't accept the parameters we'd need,
 * we just leave the private fields empty here.  OpenIntoRel will
 * fill them in.
 */
DestReceiver *
CreateIntoRelDestReceiver(void)
{
	DR_intorel *self = (DR_intorel *) palloc(sizeof(DR_intorel));

	self->pub.receiveSlot = intorel_receive;
	self->pub.rStartup = intorel_startup;
	self->pub.rShutdown = intorel_shutdown;
	self->pub.rDestroy = intorel_destroy;
	self->pub.mydest = DestIntoRel;

	self->estate = NULL;

	return (DestReceiver *) self;
}

/*
 * intorel_startup --- executor startup
 */
static void
intorel_startup(DestReceiver *self, int operation, TupleDesc typeinfo)
{
	/* no-op */
}

/*
 * intorel_receive --- receive one tuple
 */
static void
intorel_receive(TupleTableSlot *slot, DestReceiver *self)
{
	DR_intorel *myState = (DR_intorel *) self;
	EState	   *estate = myState->estate;
	HeapTuple	tuple;

	tuple = ExecCopySlotTuple(slot);

	heap_insert(estate->es_into_relation_descriptor,
				tuple,
				estate->es_snapshot->curcid,
				estate->es_into_relation_use_wal,
				false);			/* never any point in using FSM */

	/* We know this is a newly created relation, so there are no indexes */

	heap_freetuple(tuple);

	IncrAppended();
}

/*
 * intorel_shutdown --- executor end
 */
static void
intorel_shutdown(DestReceiver *self)
{
	/* no-op */
}

/*
 * intorel_destroy --- release DestReceiver object
 */
static void
intorel_destroy(DestReceiver *self)
{
	pfree(self);
}
