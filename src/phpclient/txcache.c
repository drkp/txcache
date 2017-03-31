// -*- c-file-style: "bsd" -*-

// By far the most useful documentation I've found on PHP extension
// development is http://devzone.zend.com/article/1021

#include "txcache.h"

#include "marshal.h"
#include "lib/iobuf.h"
#include "lib/iobuf-getput.h"
#include "lib/message.h"
#include "lib/latency.h"
#include "client/client.h"

ZEND_DECLARE_MODULE_GLOBALS(txcache);

DEFINE_LATENCY(roWrap);
DEFINE_LATENCY(rwWrap);
DEFINE_LATENCY(roCall);
DEFINE_LATENCY(rwCall);

#ifndef Z_ADDREF_P
#define Z_ADDREF_P(x) ZVAL_ADDREF(x)
#endif

// Copied straight out of php_pgsql.h
typedef struct _php_pgsql_result_handle {
        PGconn *conn;
        PGresult *result;
        int row;
} pgsql_result_handle;

static int le_pcache, le_pgsql_link, le_pgsql_plink, le_pgsql_result;

typedef struct cache_rsrc
{
        zval *pgconnect;
        zval *pgsql;
        PGconn *pg;

        Client_t client;
        // In do-it-anyway mode, we go to the cache, but regardless of
        // whether or not we hit, we run the cacheable function.
        // However, we distinguish the hit and miss latency
        // distributions in roCall.  This is intended to figure out if
        // we're just taking away the queries that would execute
        // quickly anyways.
        bool doItAnywayMode;
        // Whether we are running a read-only txcache transaction.
        // Any txcache transactions that are still running at
        // post-request will be committed before being migrated off
        // their Postgres connection.
        bool inXaction;
} cache_rsrc;

static void
gc_pcache(zend_rsrc_list_entry *rsrc TSRMLS_DC)
{
        cache_rsrc *c = (cache_rsrc*)rsrc->ptr;

        if (c) {
                Notice("Garbage collecting PHP cache resource");
                Client_DumpStats(&c->client);
                Client_Release(&c->client);
                if (c->pgconnect)
                        zval_ptr_dtor(&c->pgconnect);
                if (c->pgsql)
                        zval_ptr_dtor(&c->pgsql);
                pefree(c, 1);
        }
}

static void
txcache_init_globals(zend_txcache_globals *txcache_globals)
{
        // Everything is per-request, not per-thread
        // XXX Easier than getting an environment var into Apache
        //setenv("DEBUG", "client.c,txcache.c", 1);
}

PHP_MINIT_FUNCTION(txcache)
{
        ZEND_INIT_MODULE_GLOBALS(txcache, txcache_init_globals, NULL);

        le_pgsql_link = zend_fetch_list_dtor_id("pgsql link");
        if (!le_pgsql_link)
                return FAILURE;
        le_pgsql_plink = zend_fetch_list_dtor_id("pgsql link persistent");
        if (!le_pgsql_plink)
                return FAILURE;
        le_pgsql_result = zend_fetch_list_dtor_id("pgsql result");
        if (!le_pgsql_result)
                return FAILURE;

        le_pcache = zend_register_list_destructors_ex(NULL, gc_pcache,
                                                      "txcache link persistent",
                                                      module_number);

        TG(lastStatsFlush) = 0;
        return SUCCESS;
}

PHP_MSHUTDOWN_FUNCTION(txcache)
{
        Latency_Flush();
        return SUCCESS;
}

PHP_RINIT_FUNCTION(txcache)
{
        TG(pcounter) = 0;
        return SUCCESS;
}

static int
release_pgsql(zend_rsrc_list_entry *rsrc TSRMLS_DC)
{
        if (Z_TYPE_P(rsrc) != le_pcache)
                return 0;

        cache_rsrc *c = (cache_rsrc*)rsrc->ptr;

        if (c->inXaction) {
                if (!Client_Commit(&c->client))
                        Warning("Post-request commit failed");
                c->inXaction = false;
        }

        if (!c->pgconnect) {
                // Already released (this connection simply wasn't
                // used for this request)
                Assert(!c->pgsql && !c->pg);
                return ZEND_HASH_APPLY_KEEP;
        }
        zval_ptr_dtor(&c->pgconnect);
        c->pgconnect = NULL;

        if (!c->pgsql && !c->pg) {
                // Never connected
                return ZEND_HASH_APPLY_KEEP;
        }
        Assert(c->pgsql && c->pg);
        if (!Client_ReleasePG(&c->client))
                Panic("Failed to release Postgres link");
        c->pg = NULL;
        zval_ptr_dtor(&c->pgsql);
        c->pgsql = NULL;
        return ZEND_HASH_APPLY_KEEP;
}

static int
FlushStats1(zend_rsrc_list_entry *rsrc TSRMLS_DC)
{
        if (Z_TYPE_P(rsrc) != le_pcache)
                return 0;

        cache_rsrc *c = (cache_rsrc*)rsrc->ptr;
        Client_DumpStats(&c->client);
        return ZEND_HASH_APPLY_KEEP;
}

static void
FlushStats(void)
{
        zend_hash_apply(&EG(persistent_list), (apply_func_t)FlushStats1 TSRMLS_CC);
}

PHP_RSHUTDOWN_FUNCTION(txcache)
{
        // Release all of the underlying Postgresen
        zend_hash_apply(&EG(persistent_list), (apply_func_t)release_pgsql TSRMLS_CC);

        // Should we flush stats?
        struct timeval tv;
        if (gettimeofday(&tv, NULL) < 0) {
                PWarning("Failed to get time of day");
        } else {
                if (tv.tv_sec > TG(lastStatsFlush) + 30) {
                        FlushStats();
                        TG(lastStatsFlush) = tv.tv_sec;
                }
        }

        return SUCCESS;
}

static inline PGconn *
resToPGconn(zval *pgsql)
{
        return (PGconn*)zend_fetch_resource(&pgsql TSRMLS_CC, -1,
                                            "PostgreSQL link", NULL,
                                            2, le_pgsql_link, le_pgsql_plink);
}

static inline cache_rsrc *
resToCache(zval *cache)
{
        return (cache_rsrc*)zend_fetch_resource(&cache TSRMLS_CC, -1,
                                                "txcache link", NULL,
                                                1, le_pcache);
}

static PGconn *
connectcb(Client_t *client, void *opaque)
{
        cache_rsrc *c = opaque;
        if (!c->pg)
                Panic("connectcb called without a Postgres connection");
        return c->pg;
}

/**
 * resource txcache_pconnect(callback $pgconnect, bool $bypass)
 *
 * Open a connection to the txcache or re-use one if possible.  If a
 * Postgres connection is needed, pgconnect will be called and should
 * return a Postgres resource.  If $bypass is true, then the
 * connection will bypass any actual communication with the cache
 * (essentially, it will always miss in the cache), but will still
 * keep track of statistics.
 */
PHP_FUNCTION(txcache_pconnect)
{
        zval *pgconnect;
        long bypass;

        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "zl",
                                  &pgconnect, &bypass) == FAILURE) {
                RETURN_NULL();
        }

        char *pgconnectcb;
        if (!zend_is_callable(pgconnect, 0, &pgconnectcb)) {
                Warning("Not a valid callback %s", pgconnectcb);
                efree(pgconnectcb);
                RETURN_NULL();
        }

        // Get a client from the pool
        cache_rsrc *c;

        char *key;
        int key_len;
        key_len = spprintf(&key, 0, "txcache_%ld", TG(pcounter));

        zend_rsrc_list_entry *le;
        if (zend_hash_find(&EG(persistent_list), key, key_len + 1,
                           (void**) &le) == SUCCESS) {
                // Found one
                efree(key);
                // Switch it to this connection
                c = (cache_rsrc*)le->ptr;
                if (c->pgconnect)
                        Panic("Persistent cache object already has connect CB");
        } else {
                // Create a new one
#if 1
                if (TG(pcounter) > 0)
                        Warning("Multiple txcache connections");
#endif
                c = pemalloc(sizeof *c, 1);
                if (!Client_Init(&c->client, connectcb, c)) {
                        Warning("Failed to init client");
                        efree(key);
                        RETURN_NULL();
                }
                // Add it to the persistent list
                zend_rsrc_list_entry new_le;
                new_le.ptr = c;
                new_le.type = le_pcache;
                zend_hash_add(&EG(persistent_list), key, key_len + 1,
                              (void*) &new_le, sizeof new_le, NULL);
                efree(key);
        }
        // If there's another pconnect during this request,
        // give it a different client object
        TG(pcounter)++;

        c->pg = NULL;
        c->pgsql = NULL;
        c->pgconnect = pgconnect;
        Z_ADDREF_P(pgconnect);
        c->inXaction = false;

        // Set up bypass modes
        if (bypass == 0 || bypass == 1) {
                c->doItAnywayMode = false;
                Client_SetBypass(&c->client, bypass);
        } else if (bypass == 2) {
                c->doItAnywayMode = true;
                Client_SetBypass(&c->client, false);
        } else if (bypass == 3) {
                c->doItAnywayMode = false;
                Client_SetBypass(&c->client, false);
                Client_SetMemcached(&c->client, true);
        } else {
                Panic("Illegal bypass mode %ld", bypass);
        }

        // Return the txcache resource
        ZEND_REGISTER_RESOURCE(return_value, c, le_pcache);
}

/**
 * bool txcache_begin_ro(resource $cache, number $freshness)
 *
 * Begin a read-only transaction with the given freshness requirement
 * (in seconds).
 */
PHP_FUNCTION(txcache_begin_ro)
{
        zval *rc;
        double freshness;

        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "rd",
                                  &rc, &freshness) == FAILURE) {
                RETURN_NULL();
        }

        cache_rsrc *c = resToCache(rc);
        if (!c)
                RETURN_FALSE;

        bool res = Client_BeginRO(&c->client, freshness);
        if (res) {
                c->inXaction = true;
        }
        RETURN_BOOL(res);
}

/**
 * bool txcache_commit(resource $cache)
 *
 * Commit the running read-only transaction.
 */
PHP_FUNCTION(txcache_commit)
{
        zval *rc;

        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "r",
                                  &rc) == FAILURE) {
                RETURN_NULL();
        }

        cache_rsrc *c = resToCache(rc);
        if (!c)
                RETURN_FALSE;

#if 1
        // Put the final pin set in the web page
        zend_printf("<!-- \n  -- Final pin set: " FMT_PINSET "\n -->",
                    XVA_PINSET(&c->client.pinSet));
        Message_DoFrees();
#endif

        c->inXaction = false;
        RETURN_BOOL(Client_Commit(&c->client));
}

/**
 * bool txcache_add_explicit_invalidation_tag(resource $cache, string $tag)
 *
 * Explicit invalidations: add an invalidation tag to the
 * currently-executing cacheable function.
 */
PHP_FUNCTION(txcache_add_explicit_invalidation_tag)
{
        zval *rc;
        char *tag;
        int tagLen;

        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "rs",
                                  &rc, &tag, &tagLen) == FAILURE)
        {
                RETURN_NULL();
        }

        cache_rsrc *c = resToCache(rc);
        if (!c)
                RETURN_NULL();

        Client_AddExplicitInvalTag(&c->client, tag);

        RETURN_TRUE;
}

/**
 * bool txcache_explicitly_invalidate(resource $cache, string $tag)
 *
 * Explicit invalidations: invalidate a tag at commit of the current
 * transaction.
 */
PHP_FUNCTION(txcache_explicitly_invalidate)
{
        zval *rc;
        char *tag;
        int tagLen;

        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "rs",
                                  &rc, &tag, &tagLen) == FAILURE)
        {
                RETURN_NULL();
        }

        cache_rsrc *c = resToCache(rc);
        if (!c)
                RETURN_NULL();

        Client_ExplicitlyInvalidate(&c->client, tag);

        RETURN_TRUE;
}

/**
 * resource txcache_query(resource $cache, string $sql)
 *
 * Execute a SQL statement on the underlying database connection and
 * return the result object.  This works regardless of whether or not
 * there is a running read-only transaction and *must* be used instead
 * of the regular exec function.  The result object can be accessed
 * using the regular Postgres functions.
 */
PHP_FUNCTION(txcache_query)
{
        // Emulate pg_query (basically copied from PHP's pgsql.c)

        zval *rc;
        char *sql;
        int sqlLen;

        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "rs",
                                  &rc, &sql, &sqlLen) == FAILURE) {
                RETURN_NULL();
        }

        cache_rsrc *c = resToCache(rc);
        if (!c)
                RETURN_FALSE;

        // Connect to Postgres if necessary
        if (!c->pg) {
                Assert(!c->pgsql);
                zval *pglink = NULL;
                if (call_user_function_ex(EG(function_table), NULL,
                                          c->pgconnect, &pglink,
                                          0, NULL,
                                          1, NULL TSRMLS_CC) != SUCCESS) {
                        Warning("Failed to call pgconnect function");
                        RETURN_FALSE;
                }
                PGconn *pg = resToPGconn(pglink);
                if (!pg) {
                        Warning("pgconnect returned non-Postgres resource");
                        RETURN_FALSE;
                }
                c->pg = pg;
                c->pgsql = pglink;
                Z_ADDREF_P(pglink);
        }

        if (PQsetnonblocking(c->pg, 0)) {
                Warning("Cannot set connection to blocking mode");
                RETURN_FALSE;
        }

        PGresult *toss = NULL;
        while ((toss = PQgetResult(c->pg))) {
                PQclear(toss);
        }
        if (toss) {
                Warning("Found unconsumed results on the Postgres connection");
        }

        PGresult *res = Client_Exec(&c->client, sql);

        ExecStatusType status;
        if (res) {
                status = PQresultStatus(res);
        } else {
                status = (ExecStatusType) PQstatus(c->pg);
        }

        switch (status) {
        case PGRES_EMPTY_QUERY:
        case PGRES_BAD_RESPONSE:
        case PGRES_NONFATAL_ERROR:
        case PGRES_FATAL_ERROR:
                Warning("Query failed: %s", PQerrorMessage(c->pg));
                PQclear(res);
                RETURN_FALSE;
                break;
        case PGRES_COMMAND_OK:
                // Successful command that did not return rows
        default:
                if (res) {
                        pgsql_result_handle *pg_result;
                        pg_result = emalloc(sizeof *pg_result);
                        pg_result->conn = c->pg;
                        pg_result->result = res;
                        pg_result->row = 0;
                        ZEND_REGISTER_RESOURCE(return_value, pg_result,
                                               le_pgsql_result);
                } else {
                        PQclear(res);
                        RETURN_FALSE;
                }
                break;
        }
}

/**
 * mixed txcache_wrap(resource $cache, bool explicitInvalidations, callback $fn[, mixed $param, ...])
 *
 * Make a cacheable call to the given function, passing the given
 * arguments.  All of the forwarded arguments must be marshallable,
 * except for the first, which may be a resource.  This is to support
 * calling conventions where the database resource is passed as the
 * first argument to functions.  This may be used regardless of
 * whether or not there is a running read-only transaction.  Returns
 * the result of the function, which may either come from running the
 * function or from the cache.
 */
PHP_FUNCTION(txcache_wrap)
{
        zval ***args;
        int argc = ZEND_NUM_ARGS();

        if (argc < 3) {
                WRONG_PARAM_COUNT;
        }

        args = (zval ***)safe_emalloc(argc, sizeof **args, 0);
        if (zend_get_parameters_array_ex(argc, args) == FAILURE) {
                efree(args);
                WRONG_PARAM_COUNT;
        }

        cache_rsrc *c = resToCache(*args[0]);
        if (!c)
                RETURN_NULL();

        if (Z_TYPE_PP(args[1]) != IS_BOOL)
        {
                Warning("explicitInvalidations was not a boolean");
                efree(args);
                RETURN_NULL();
        }
        bool explicitInvalidations = (Z_LVAL_PP(args[1]) != 0);

        zval *fn = *args[2];
        char *callback_name;
        if (!zend_is_callable(fn, 0, &callback_name)) {
                Warning("Not a valid callback %s", callback_name);
                efree(callback_name);
                efree(args);
                RETURN_NULL();
        }
        
        Debug("Wrapping user function %s", callback_name);

        bool tryCache = Client_MightCache(&c->client);

        Latency_t *latency = tryCache ? &roWrap : &rwWrap,
                *callLatency = tryCache ? &roCall : &rwCall;
        Latency_Frame_t latencyFrame, callLatencyFrame;
        Latency_StartRec(latency, &latencyFrame);

        bool status = false;
        bool framePushed = false;
        bool bufferPushed = false;
        bool bailout = false;

        ClientCacheableFrame_t frame;
        IOBuf_t key = IOBUF_NULL;
        int obNesting = 0;      // Initialize to placate gcc

        if (tryCache) {
                // Serialize key
                if (!IOBuf_Init(&key))
                        Panic("Failed to initialize key IOBuf");
                // XXX Could hash the function name
                IOBuf_PutString(&key, callback_name);
                // Support calling conventions where the first
                // function argument is a database resource and where
                // the database resource is tracked globally.
                int first = 3;
                if (argc > 3 && Z_TYPE_P(*args[3]) == IS_RESOURCE)
                        first = 4;
                for (int i = first; i < argc; ++i) {
                        if (!Marshal_PutZVal(&key, *args[i])) {
                                goto end;
                        }
                }
                if (IOBuf_Error(&key)) {
                        Warning("Failed to create key: %s",
                                strerror(IOBuf_Error(&key)));
                        goto end;
                }

                // Get key data
                size_t keyLen;
                const void *keyData = IOBuf_PeekRest(&key, &keyLen);
                if (!keyData)
                        Panic("Failed to get key data");

                Debug("Key data for call to %s", callback_name);
                Message_Hexdump(keyData, keyLen);

                size_t resultLen;
                void *resultData =
                        Client_TryEnterCacheable(&c->client, keyData, keyLen,
                                                 &resultLen, &frame);
                if (resultData && !c->doItAnywayMode) {
                        Debug("Cache hit for %s", callback_name);
                        // Deserialize return data
                        IOBuf_t result;
                        if (!IOBuf_InitView(&result, resultData, resultLen))
                                Panic("Failed to initialize view");
                        if (!Marshal_GetZVal(&result, return_value))
                                Panic("Failed to unmarshal return value");
                        // Deserialize echo data
                        size_t obBufLen;
                        const char *obBuf = IOBuf_TryGetBuf(&result, &obBufLen);
                        if (!obBuf)
                                Panic("Failed to unmarshal output buffer");
                        zend_write(obBuf, obBufLen);
                        IOBuf_Release(&result);
                        Latency_EndRecType(latency, &latencyFrame, 'h');
                        return;
                } else if (resultData && c->doItAnywayMode) {
                        Debug("Cache hit for %s, but we're doing it anyway!",
                              callback_name);
                        framePushed = false;
                } else {
                        frame.willingToDealWithInvalidations
                                = explicitInvalidations;
                        framePushed = true;
                }
                if (php_start_ob_buffer(NULL, 0, 1) == FAILURE) {
                        // XXX
                        Panic("php_start_ob_buffer");
                }
                bufferPushed = true;
                obNesting = OG(ob_nesting_level);
        }

        zend_try {
                zval *retval_ptr;
                Latency_Pause(latency);
                Latency_StartRec(callLatency, &callLatencyFrame);
                if (call_user_function_ex(EG(function_table), NULL, fn, &retval_ptr,
                                          argc - 3, args + 3,
                                          0, NULL TSRMLS_CC) == SUCCESS) {
                        if (!c->doItAnywayMode)
                                Latency_EndRec(callLatency, &callLatencyFrame);
                        else if (framePushed)
                                Latency_EndRecType(callLatency,
                                                   &callLatencyFrame, 'M');
                        else
                                Latency_EndRecType(callLatency,
                                                   &callLatencyFrame, 'h');

                        RETVAL_ZVAL(retval_ptr, 0, 1);
                        status = true;
                } else {
                        Latency_EndRecType(callLatency, &callLatencyFrame, '!');
                        Warning("Unable to call %s()", callback_name);
                }
        } zend_catch {
                Latency_EndRecType(callLatency, &callLatencyFrame, 'A');
                Debug("User function %s threw something", callback_name);
                bailout = true;
                status = false;
        } zend_end_try();
        Latency_Resume(latency);

end:
#if 0
        Debug("status %d bailout %d framePushed %d bufferPushed %d",
              status, bailout, framePushed, bufferPushed);
#endif
        if (framePushed) {
                if (status && obNesting != OG(ob_nesting_level)) {
                        Warning("Output buffering nesting level changed");
                        // We can't cache it since we have no idea
                        // what actually happened
                        status = false;
                }
                // Serialize result
                IOBuf_t result = IOBUF_NULL;
                if (status) {
                        if (!IOBuf_Init(&result))
                                Panic("Failed to initialize result IOBuf");
                        if (!Marshal_PutZVal(&result, return_value)) {
                                status = false;
                        } else {
                                IOBuf_PutBuf(&result,
                                             OG(active_ob_buffer).buffer,
                                             OG(active_ob_buffer).text_length);
                                if (IOBuf_Error(&result)) {
                                        Warning("Failed to marshal result: %s",
                                                strerror(IOBuf_Error(&result)));
                                        status = false;
                                }
                        }
                }
                if (status) {
                        size_t resultLen;
                        const void *resultData =
                                IOBuf_PeekRest(&result, &resultLen);
                        if (!resultData)
                                Panic("Failed to get result data");
                        Debug("Result data for %s", callback_name);
                        Message_Hexdump(resultData, resultLen);
                        Client_ExitCacheable(&frame, resultData, resultLen);
                } else {
                        Debug("Aborting frame for %s", callback_name);
                        Client_ExitCacheableAbort(&frame);
                }
                IOBuf_Release(&result);
        }

        if (bufferPushed)
                php_end_ob_buffer(1, 0 TSRMLS_CC);

        IOBuf_Release(&key);
        efree(callback_name);
        efree(args);

        Latency_EndRecType(latency, &latencyFrame,
                           framePushed ? (status ? 'M' : 'A') : '!');

        if (bailout) {
                zend_bailout();
        }
}

PHP_FUNCTION(txcache_test_marshal)
{
        zval *val;
        
        if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "z",
                                  &val) == FAILURE) {
                RETURN_NULL();
        }

        IOBuf_t buf;
        if (!IOBuf_Init(&buf))
                Panic("Failed to initialize IOBuf");

        if (!Marshal_PutZVal(&buf, val)) {
                IOBuf_Release(&buf);
                Warning("Failed to marshal value");
                RETURN_FALSE;
        }
        if (!Marshal_GetZVal(&buf, return_value)) {
                IOBuf_Release(&buf);
                Warning("Failed to unmarshal value");
                RETURN_FALSE;
        }

        IOBuf_Release(&buf);
}

static function_entry txcache_functions[] = {
        PHP_FE(txcache_pconnect, NULL)
        PHP_FE(txcache_begin_ro, NULL)
        PHP_FE(txcache_commit, NULL)
        PHP_FE(txcache_add_explicit_invalidation_tag, NULL)
        PHP_FE(txcache_explicitly_invalidate, NULL)
        PHP_FE(txcache_query, NULL)
        PHP_FE(txcache_wrap, NULL)
        PHP_FE(txcache_test_marshal, NULL)
        {NULL, NULL, NULL}
};

zend_module_entry txcache_module_entry = {
#if ZEND_MODULE_API_NO >= 20010901
        STANDARD_MODULE_HEADER,
#endif
        PHP_TXCACHE_EXTNAME,
        txcache_functions,
        PHP_MINIT(txcache),
        PHP_MSHUTDOWN(txcache),
        PHP_RINIT(txcache),
        PHP_RSHUTDOWN(txcache),
        NULL,
#if ZEND_MODULE_API_NO >= 20010901
        PHP_TXCACHE_VERSION,
#endif
        STANDARD_MODULE_PROPERTIES
};

ZEND_GET_MODULE(txcache);
