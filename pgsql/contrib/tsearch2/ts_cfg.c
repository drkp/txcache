/*
 * interface functions to tscfg
 * Teodor Sigaev <teodor@sigaev.ru>
 */
#include "postgres.h"

#include <ctype.h>
#include <locale.h>

#include "catalog/pg_type.h"
#include "executor/spi.h"
#include "fmgr.h"
#include "utils/array.h"
#include "utils/memutils.h"

#include "ts_cfg.h"
#include "dict.h"
#include "wparser.h"
#include "snmap.h"
#include "common.h"
#include "tsvector.h"

PG_MODULE_MAGIC;

#define IGNORE_LONGLEXEME	1

/*********top interface**********/

static Oid	current_cfg_id = 0;

void
init_cfg(Oid id, TSCfgInfo * cfg)
{
	Oid			arg[2];
	bool		isnull;
	Datum		pars[2];
	int			stat,
				i,
				j;
	text	   *ptr;
	text	   *prsname = NULL;
	char	   *nsp = get_namespace(TSNSP_FunctionOid);
	char		buf[1024];
	MemoryContext oldcontext;
	void	   *plan;

	arg[0] = OIDOID;
	arg[1] = OIDOID;
	pars[0] = ObjectIdGetDatum(id);
	pars[1] = ObjectIdGetDatum(id);

	memset(cfg, 0, sizeof(TSCfgInfo));
	SPI_connect();

	sprintf(buf, "select prs_name from %s.pg_ts_cfg where oid = $1", nsp);
	plan = SPI_prepare(buf, 1, arg);
	if (!plan)
		ts_error(ERROR, "SPI_prepare() failed");

	stat = SPI_execp(plan, pars, " ", 1);
	if (stat < 0)
		ts_error(ERROR, "SPI_execp return %d", stat);
	if (SPI_processed > 0)
	{
		prsname = (text *) DatumGetPointer(
										   SPI_getbinval(SPI_tuptable->vals[0], SPI_tuptable->tupdesc, 1, &isnull)
			);
		oldcontext = MemoryContextSwitchTo(TopMemoryContext);
		prsname = ptextdup(prsname);
		MemoryContextSwitchTo(oldcontext);

		cfg->id = id;
	}
	else
		ts_error(ERROR, "No tsearch cfg with id %d", id);

	SPI_freeplan(plan);

	arg[0] = TEXTOID;
	sprintf(buf, "select lt.tokid, map.dict_name from %s.pg_ts_cfgmap as map, %s.pg_ts_cfg as cfg, %s.token_type( $1 ) as lt where lt.alias =  map.tok_alias and map.ts_name = cfg.ts_name and cfg.oid= $2 order by lt.tokid desc;", nsp, nsp, nsp);
	plan = SPI_prepare(buf, 2, arg);
	if (!plan)
		ts_error(ERROR, "SPI_prepare() failed");

	pars[0] = PointerGetDatum(prsname);
	stat = SPI_execp(plan, pars, " ", 0);
	if (stat < 0)
		ts_error(ERROR, "SPI_execp return %d", stat);
	if (SPI_processed <= 0)
		ts_error(ERROR, "No parser with id %d", id);

	for (i = 0; i < SPI_processed; i++)
	{
		int			lexid = DatumGetInt32(SPI_getbinval(SPI_tuptable->vals[i], SPI_tuptable->tupdesc, 1, &isnull));
		ArrayType  *toasted_a = (ArrayType *) PointerGetDatum(SPI_getbinval(SPI_tuptable->vals[i], SPI_tuptable->tupdesc, 2, &isnull));
		ArrayType  *a;

		if (!cfg->map)
		{
			cfg->len = lexid + 1;
			cfg->map = (ListDictionary *) malloc(sizeof(ListDictionary) * cfg->len);
			if (!cfg->map)
				ereport(ERROR,
						(errcode(ERRCODE_OUT_OF_MEMORY),
						 errmsg("out of memory")));
			memset(cfg->map, 0, sizeof(ListDictionary) * cfg->len);
		}

		if (isnull)
			continue;

		a = (ArrayType *) PointerGetDatum(PG_DETOAST_DATUM(DatumGetPointer(toasted_a)));

		if (ARR_NDIM(a) != 1)
			ts_error(ERROR, "Wrong dimension");
		if (ARRNELEMS(a) < 1)
			continue;
		if (ARR_HASNULL(a))
			ts_error(ERROR, "Array must not contain nulls");

		cfg->map[lexid].len = ARRNELEMS(a);
		cfg->map[lexid].dict_id = (Datum *) malloc(sizeof(Datum) * cfg->map[lexid].len);
		if (!cfg->map[lexid].dict_id)
			ts_error(ERROR, "No memory");

		memset(cfg->map[lexid].dict_id, 0, sizeof(Datum) * cfg->map[lexid].len);
		ptr = (text *) ARR_DATA_PTR(a);
		oldcontext = MemoryContextSwitchTo(TopMemoryContext);
		for (j = 0; j < cfg->map[lexid].len; j++)
		{
			cfg->map[lexid].dict_id[j] = PointerGetDatum(ptextdup(ptr));
			ptr = NEXTVAL(ptr);
		}
		MemoryContextSwitchTo(oldcontext);

		if (a != toasted_a)
			pfree(a);
	}

	SPI_freeplan(plan);
	SPI_finish();
	cfg->prs_id = name2id_prs(prsname);
	pfree(prsname);
	pfree(nsp);
	for (i = 0; i < cfg->len; i++)
	{
		for (j = 0; j < cfg->map[i].len; j++)
		{
			ptr = (text *) DatumGetPointer(cfg->map[i].dict_id[j]);
			cfg->map[i].dict_id[j] = ObjectIdGetDatum(name2id_dict(ptr));
			pfree(ptr);
		}
	}
}

typedef struct
{
	TSCfgInfo  *last_cfg;
	int			len;
	int			reallen;
	TSCfgInfo  *list;
	SNMap		name2id_map;
}	CFGList;

static CFGList CList = {NULL, 0, 0, NULL, {0, 0, NULL}};

void
reset_cfg(void)
{
	freeSNMap(&(CList.name2id_map));
	if (CList.list)
	{
		int			i,
					j;

		for (i = 0; i < CList.len; i++)
			if (CList.list[i].map)
			{
				for (j = 0; j < CList.list[i].len; j++)
					if (CList.list[i].map[j].dict_id)
						free(CList.list[i].map[j].dict_id);
				free(CList.list[i].map);
			}
		free(CList.list);
	}
	memset(&CList, 0, sizeof(CFGList));
}

static int
comparecfg(const void *a, const void *b)
{
	if (((TSCfgInfo *) a)->id == ((TSCfgInfo *) b)->id)
		return 0;
	return (((TSCfgInfo *) a)->id < ((TSCfgInfo *) b)->id) ? -1 : 1;
}

TSCfgInfo *
findcfg(Oid id)
{
	/* last used cfg */
	if (CList.last_cfg && CList.last_cfg->id == id)
		return CList.last_cfg;

	/* already used cfg */
	if (CList.len != 0)
	{
		TSCfgInfo	key;

		key.id = id;
		CList.last_cfg = bsearch(&key, CList.list, CList.len, sizeof(TSCfgInfo), comparecfg);
		if (CList.last_cfg != NULL)
			return CList.last_cfg;
	}

	/* last chance */
	if (CList.len == CList.reallen)
	{
		TSCfgInfo  *tmp;
		int			reallen = (CList.reallen) ? 2 * CList.reallen : 16;

		tmp = (TSCfgInfo *) realloc(CList.list, sizeof(TSCfgInfo) * reallen);
		if (!tmp)
			ts_error(ERROR, "No memory");
		CList.reallen = reallen;
		CList.list = tmp;
	}
	init_cfg(id, &(CList.list[CList.len]) );
	CList.last_cfg = &(CList.list[CList.len]);
	CList.len++;
	qsort(CList.list, CList.len, sizeof(TSCfgInfo), comparecfg);
	return findcfg(id); /* qsort changed order!! */ ;
}


Oid
name2id_cfg(text *name)
{
	Oid			arg[1];
	bool		isnull;
	Datum		pars[1];
	int			stat;
	Oid			id = findSNMap_t(&(CList.name2id_map), name);
	void	   *plan;
	char	   *nsp;
	char		buf[1024];

	arg[0] = TEXTOID;
	pars[0] = PointerGetDatum(name);

	if (id)
		return id;

	nsp = get_namespace(TSNSP_FunctionOid);
	SPI_connect();
	sprintf(buf, "select oid from %s.pg_ts_cfg where ts_name = $1", nsp);
	plan = SPI_prepare(buf, 1, arg);
	if (!plan)
		/* internal error */
		elog(ERROR, "SPI_prepare() failed");

	stat = SPI_execp(plan, pars, " ", 1);
	if (stat < 0)
		/* internal error */
		elog(ERROR, "SPI_execp return %d", stat);
	if (SPI_processed > 0)
	{
		id = DatumGetObjectId(SPI_getbinval(SPI_tuptable->vals[0], SPI_tuptable->tupdesc, 1, &isnull));
		if (isnull)
			ereport(ERROR,
					(errcode(ERRCODE_CONFIG_FILE_ERROR),
					 errmsg("null id for tsearch config")));
	}
	else
		ereport(ERROR,
				(errcode(ERRCODE_CONFIG_FILE_ERROR),
				 errmsg("no tsearch config")));

	SPI_freeplan(plan);
	SPI_finish();
	addSNMap_t(&(CList.name2id_map), name, id);
	return id;
}

void
parsetext_v2(TSCfgInfo * cfg, PRSTEXT * prs, char *buf, int4 buflen)
{
	int			type,
				lenlemm;
	char	   *lemm = NULL;
	WParserInfo *prsobj = findprs(cfg->prs_id);
	LexizeData	ldata;
	TSLexeme   *norms;

	prsobj->prs = (void *) DatumGetPointer(
										   FunctionCall2(
													   &(prsobj->start_info),
														 PointerGetDatum(buf),
														 Int32GetDatum(buflen)
														 )
		);

	LexizeInit(&ldata, cfg);

	do
	{
		type = DatumGetInt32(FunctionCall3(
										   &(prsobj->getlexeme_info),
										   PointerGetDatum(prsobj->prs),
										   PointerGetDatum(&lemm),
										   PointerGetDatum(&lenlemm)));

		if (type > 0 && lenlemm >= MAXSTRLEN)
		{
#ifdef IGNORE_LONGLEXEME
			ereport(NOTICE,
					(errcode(ERRCODE_SYNTAX_ERROR),
					 errmsg("A word you are indexing is too long. It will be ignored.")));
			continue;
#else
			ereport(ERROR,
					(errcode(ERRCODE_SYNTAX_ERROR),
					 errmsg("A word you are indexing is too long")));
#endif
		}

		LexizeAddLemm(&ldata, type, lemm, lenlemm);

		while ((norms = LexizeExec(&ldata, NULL)) != NULL)
		{
			TSLexeme   *ptr = norms;

			prs->pos++;			/* set pos */

			while (ptr->lexeme)
			{
				if (prs->curwords == prs->lenwords)
				{
					prs->lenwords *= 2;
					prs->words = (TSWORD *) repalloc((void *) prs->words, prs->lenwords * sizeof(TSWORD));
				}

				if (ptr->flags & TSL_ADDPOS)
					prs->pos++;
				prs->words[prs->curwords].len = strlen(ptr->lexeme);
				prs->words[prs->curwords].word = ptr->lexeme;
				prs->words[prs->curwords].nvariant = ptr->nvariant;
				prs->words[prs->curwords].alen = 0;
				prs->words[prs->curwords].pos.pos = LIMITPOS(prs->pos);
				ptr++;
				prs->curwords++;
			}
			pfree(norms);
		}
	} while (type > 0);

	FunctionCall1(
				  &(prsobj->end_info),
				  PointerGetDatum(prsobj->prs)
		);
}

static void
hladdword(HLPRSTEXT * prs, char *buf, int4 buflen, int type)
{
	while (prs->curwords >= prs->lenwords)
	{
		prs->lenwords *= 2;
		prs->words = (HLWORD *) repalloc((void *) prs->words, prs->lenwords * sizeof(HLWORD));
	}
	memset(&(prs->words[prs->curwords]), 0, sizeof(HLWORD));
	prs->words[prs->curwords].type = (uint8) type;
	prs->words[prs->curwords].len = buflen;
	prs->words[prs->curwords].word = palloc(buflen);
	memcpy(prs->words[prs->curwords].word, buf, buflen);
	prs->curwords++;
}

static void
hlfinditem(HLPRSTEXT * prs, QUERYTYPE * query, char *buf, int buflen)
{
	int			i;
	ITEM	   *item = GETQUERY(query);
	HLWORD	   *word;

	while (prs->curwords + query->size >= prs->lenwords)
	{
		prs->lenwords *= 2;
		prs->words = (HLWORD *) repalloc((void *) prs->words, prs->lenwords * sizeof(HLWORD));
	}

	word = &(prs->words[prs->curwords - 1]);
	for (i = 0; i < query->size; i++)
	{
		if (item->type == VAL && item->length == buflen && strncmp(GETOPERAND(query) + item->distance, buf, buflen) == 0)
		{
			if (word->item)
			{
				memcpy(&(prs->words[prs->curwords]), word, sizeof(HLWORD));
				prs->words[prs->curwords].item = item;
				prs->words[prs->curwords].repeated = 1;
				prs->curwords++;
			}
			else
				word->item = item;
		}
		item++;
	}
}

static void
addHLParsedLex(HLPRSTEXT * prs, QUERYTYPE * query, ParsedLex * lexs, TSLexeme * norms)
{
	ParsedLex  *tmplexs;
	TSLexeme   *ptr;

	while (lexs)
	{

		if (lexs->type > 0)
			hladdword(prs, lexs->lemm, lexs->lenlemm, lexs->type);

		ptr = norms;
		while (ptr && ptr->lexeme)
		{
			hlfinditem(prs, query, ptr->lexeme, strlen(ptr->lexeme));
			ptr++;
		}

		tmplexs = lexs->next;
		pfree(lexs);
		lexs = tmplexs;
	}

	if (norms)
	{
		ptr = norms;
		while (ptr->lexeme)
		{
			pfree(ptr->lexeme);
			ptr++;
		}
		pfree(norms);
	}
}

void
hlparsetext(TSCfgInfo * cfg, HLPRSTEXT * prs, QUERYTYPE * query, char *buf, int4 buflen)
{
	int			type,
				lenlemm;
	char	   *lemm = NULL;
	WParserInfo *prsobj = findprs(cfg->prs_id);
	LexizeData	ldata;
	TSLexeme   *norms;
	ParsedLex  *lexs;

	prsobj->prs = (void *) DatumGetPointer(
										   FunctionCall2(
													   &(prsobj->start_info),
														 PointerGetDatum(buf),
														 Int32GetDatum(buflen)
														 )
		);

	LexizeInit(&ldata, cfg);

	do
	{
		type = DatumGetInt32(FunctionCall3(
										   &(prsobj->getlexeme_info),
										   PointerGetDatum(prsobj->prs),
										   PointerGetDatum(&lemm),
										   PointerGetDatum(&lenlemm)));

		if (type > 0 && lenlemm >= MAXSTRLEN)
		{
#ifdef IGNORE_LONGLEXEME
			ereport(NOTICE,
					(errcode(ERRCODE_SYNTAX_ERROR),
					 errmsg("A word you are indexing is too long. It will be ignored.")));
			continue;
#else
			ereport(ERROR,
					(errcode(ERRCODE_SYNTAX_ERROR),
					 errmsg("A word you are indexing is too long")));
#endif
		}

		LexizeAddLemm(&ldata, type, lemm, lenlemm);

		do
		{
			if ((norms = LexizeExec(&ldata, &lexs)) != NULL)
				addHLParsedLex(prs, query, lexs, norms);
			else
				addHLParsedLex(prs, query, lexs, NULL);
		} while (norms);

	} while (type > 0);

	FunctionCall1(
				  &(prsobj->end_info),
				  PointerGetDatum(prsobj->prs)
		);
}

text *
genhl(HLPRSTEXT * prs)
{
	text	   *out;
	int			len = 128;
	char	   *ptr;
	HLWORD	   *wrd = prs->words;

	out = (text *) palloc(len);
	ptr = ((char *) out) + VARHDRSZ;

	while (wrd - prs->words < prs->curwords)
	{
		while (wrd->len + prs->stopsellen + prs->startsellen + (ptr - ((char *) out)) >= len)
		{
			int			dist = ptr - ((char *) out);

			len *= 2;
			out = (text *) repalloc(out, len);
			ptr = ((char *) out) + dist;
		}

		if (wrd->in && !wrd->repeated)
		{
			if (wrd->replace)
			{
				*ptr = ' ';
				ptr++;
			}
			else if (!wrd->skip)
			{
				if (wrd->selected)
				{
					memcpy(ptr, prs->startsel, prs->startsellen);
					ptr += prs->startsellen;
				}
				memcpy(ptr, wrd->word, wrd->len);
				ptr += wrd->len;
				if (wrd->selected)
				{
					memcpy(ptr, prs->stopsel, prs->stopsellen);
					ptr += prs->stopsellen;
				}
			}
		}
		else if (!wrd->repeated)
			pfree(wrd->word);

		wrd++;
	}

	VARATT_SIZEP(out) = ptr - ((char *) out);
	return out;
}

int
get_currcfg(void)
{
	Oid			arg[1] = {TEXTOID};
	const char *curlocale;
	Datum		pars[1];
	bool		isnull;
	int			stat;
	char		buf[1024];
	char	   *nsp;
	void	   *plan;

	if (current_cfg_id > 0)
		return current_cfg_id;

	nsp = get_namespace(TSNSP_FunctionOid);
	SPI_connect();
	sprintf(buf, "select oid from %s.pg_ts_cfg where locale = $1 ", nsp);
	pfree(nsp);
	plan = SPI_prepare(buf, 1, arg);
	if (!plan)
		/* internal error */
		elog(ERROR, "SPI_prepare() failed");

	curlocale = setlocale(LC_CTYPE, NULL);
	pars[0] = PointerGetDatum(char2text((char *) curlocale));
	stat = SPI_execp(plan, pars, " ", 1);

	if (stat < 0)
		/* internal error */
		elog(ERROR, "SPI_execp return %d", stat);
	if (SPI_processed > 0)
		current_cfg_id = DatumGetObjectId(SPI_getbinval(SPI_tuptable->vals[0], SPI_tuptable->tupdesc, 1, &isnull));
	else
		ereport(ERROR,
				(errcode(ERRCODE_CONFIG_FILE_ERROR),
				 errmsg("could not find tsearch config by locale")));

	pfree(DatumGetPointer(pars[0]));
	SPI_freeplan(plan);
	SPI_finish();
	return current_cfg_id;
}

PG_FUNCTION_INFO_V1(set_curcfg);
Datum		set_curcfg(PG_FUNCTION_ARGS);
Datum
set_curcfg(PG_FUNCTION_ARGS)
{
	SET_FUNCOID();
	findcfg(PG_GETARG_OID(0));
	current_cfg_id = PG_GETARG_OID(0);
	PG_RETURN_VOID();
}

PG_FUNCTION_INFO_V1(set_curcfg_byname);
Datum		set_curcfg_byname(PG_FUNCTION_ARGS);
Datum
set_curcfg_byname(PG_FUNCTION_ARGS)
{
	text	   *name = PG_GETARG_TEXT_P(0);

	SET_FUNCOID();
	DirectFunctionCall1(
						set_curcfg,
						ObjectIdGetDatum(name2id_cfg(name))
		);
	PG_FREE_IF_COPY(name, 0);
	PG_RETURN_VOID();
}

PG_FUNCTION_INFO_V1(show_curcfg);
Datum		show_curcfg(PG_FUNCTION_ARGS);
Datum
show_curcfg(PG_FUNCTION_ARGS)
{
	SET_FUNCOID();
	PG_RETURN_OID(get_currcfg());
}

PG_FUNCTION_INFO_V1(reset_tsearch);
Datum		reset_tsearch(PG_FUNCTION_ARGS);
Datum
reset_tsearch(PG_FUNCTION_ARGS)
{
	SET_FUNCOID();
	ts_error(NOTICE, "TSearch cache cleaned");
	PG_RETURN_VOID();
}
