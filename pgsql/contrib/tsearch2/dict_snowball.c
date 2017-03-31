/* $PostgreSQL: pgsql/contrib/tsearch2/dict_snowball.c,v 1.13 2006/11/20 14:03:30 teodor Exp $ */

/*
 * example of Snowball dictionary
 * http://snowball.tartarus.org/
 * Teodor Sigaev <teodor@sigaev.ru>
 */
#include "postgres.h"

#include "dict.h"
#include "common.h"
#include "snowball/english_stem.h"
#include "snowball/header.h"
#include "snowball/russian_stem.h"
#include "snowball/russian_stem_UTF8.h"
#include "ts_locale.h"

typedef struct
{
	struct SN_env *z;
	StopList	stoplist;
	int			(*stem) (struct SN_env * z);
}	DictSnowball;


PG_FUNCTION_INFO_V1(snb_en_init);
Datum		snb_en_init(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(snb_ru_init_koi8);
Datum		snb_ru_init_koi8(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(snb_ru_init_utf8);
Datum		snb_ru_init_utf8(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(snb_lexize);
Datum		snb_lexize(PG_FUNCTION_ARGS);

Datum
snb_en_init(PG_FUNCTION_ARGS)
{
	DictSnowball *d = (DictSnowball *) malloc(sizeof(DictSnowball));

	if (!d)
		ereport(ERROR,
				(errcode(ERRCODE_OUT_OF_MEMORY),
				 errmsg("out of memory")));
	memset(d, 0, sizeof(DictSnowball));
	d->stoplist.wordop = lowerstr;

	if (!PG_ARGISNULL(0) && PG_GETARG_POINTER(0) != NULL)
	{
		text	   *in = PG_GETARG_TEXT_P(0);

		readstoplist(in, &(d->stoplist));
		sortstoplist(&(d->stoplist));
		PG_FREE_IF_COPY(in, 0);
	}

	d->z = english_ISO_8859_1_create_env();
	if (!d->z)
	{
		freestoplist(&(d->stoplist));
		ereport(ERROR,
				(errcode(ERRCODE_OUT_OF_MEMORY),
				 errmsg("out of memory")));
	}
	d->stem = english_ISO_8859_1_stem;

	PG_RETURN_POINTER(d);
}

Datum
snb_ru_init_koi8(PG_FUNCTION_ARGS)
{
	DictSnowball *d = (DictSnowball *) malloc(sizeof(DictSnowball));

	if (!d)
		ereport(ERROR,
				(errcode(ERRCODE_OUT_OF_MEMORY),
				 errmsg("out of memory")));
	memset(d, 0, sizeof(DictSnowball));
	d->stoplist.wordop = lowerstr;

	if (!PG_ARGISNULL(0) && PG_GETARG_POINTER(0) != NULL)
	{
		text	   *in = PG_GETARG_TEXT_P(0);

		readstoplist(in, &(d->stoplist));
		sortstoplist(&(d->stoplist));
		PG_FREE_IF_COPY(in, 0);
	}

	d->z = russian_KOI8_R_create_env();
	if (!d->z)
	{
		freestoplist(&(d->stoplist));
		ereport(ERROR,
				(errcode(ERRCODE_OUT_OF_MEMORY),
				 errmsg("out of memory")));
	}
	d->stem = russian_KOI8_R_stem;

	PG_RETURN_POINTER(d);
}

Datum
snb_ru_init_utf8(PG_FUNCTION_ARGS)
{
	DictSnowball *d = (DictSnowball *) malloc(sizeof(DictSnowball));

	if (!d)
		ereport(ERROR,
				(errcode(ERRCODE_OUT_OF_MEMORY),
				 errmsg("out of memory")));
	memset(d, 0, sizeof(DictSnowball));
	d->stoplist.wordop = lowerstr;

	if (!PG_ARGISNULL(0) && PG_GETARG_POINTER(0) != NULL)
	{
		text	   *in = PG_GETARG_TEXT_P(0);

		readstoplist(in, &(d->stoplist));
		sortstoplist(&(d->stoplist));
		PG_FREE_IF_COPY(in, 0);
	}

	d->z = russian_UTF_8_create_env();
	if (!d->z)
	{
		freestoplist(&(d->stoplist));
		ereport(ERROR,
				(errcode(ERRCODE_OUT_OF_MEMORY),
				 errmsg("out of memory")));
	}
	d->stem = russian_UTF_8_stem;

	PG_RETURN_POINTER(d);
}

Datum
snb_lexize(PG_FUNCTION_ARGS)
{
	DictSnowball *d = (DictSnowball *) PG_GETARG_POINTER(0);
	char	   *in = (char *) PG_GETARG_POINTER(1);
	char	   *utxt = pnstrdup(in, PG_GETARG_INT32(2));
	TSLexeme   *res = palloc(sizeof(TSLexeme) * 2);
	char	   *txt = lowerstr(utxt);
	
	pfree(utxt);
	memset(res, 0, sizeof(TSLexeme) * 2);
	if (*txt == '\0' || searchstoplist(&(d->stoplist), txt))
	{
		pfree(txt);
	}
	else
	{
		SN_set_current(d->z, strlen(txt), (symbol *) txt);
		(d->stem) (d->z);
		if (d->z->p && d->z->l)
		{
			txt = repalloc(txt, d->z->l + 1);
			memcpy(txt, d->z->p, d->z->l);
			txt[d->z->l] = '\0';
		}
		res->lexeme = txt;
	}

	PG_RETURN_POINTER(res);
}
