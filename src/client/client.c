// -*- c-file-style: "bsd" -*-

#define _GNU_SOURCE

#include "client.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "pincushion/proxy.h"
#include "server/proxy.h"
#include "lib/hash.h"
#include "lib/message.h"
#include "lib/timeval.h"
#include "lib/latency.h"

DEFINE_LATENCY(connectLatency);
DEFINE_LATENCY(roQueryLatency);
DEFINE_LATENCY(rwQueryLatency);
DEFINE_LATENCY(pincushionRequestLatency);
DEFINE_LATENCY(pincushionReleaseLatency);
DEFINE_LATENCY(pincushionInsertLatency);
DEFINE_LATENCY(dbCommitLatency);

static void
ClientInvalTagSet_InitValue(ClientInvalTag_t *val,
                            ClientInvalTag_t key)
{
        val->tag = malloc(strlen(key.tag)+1);
        strcpy(val->tag, key.tag);
}

static void
ClientInvalTagSet_ReleaseValue(ClientInvalTag_t *val)
{
        free(val->tag);
}

static int
ClientInvalTagSet_CompareValue(ClientInvalTag_t *val,
                               ClientInvalTag_t key)
{
        return strcmp(val->tag, key.tag);
}

%instance RBTreeImpl ClientInvalTagSet(ClientInvalTag_t, ClientInvalTag_t,
                                       ClientInvalTagSet_InitValue,
                                       ClientInvalTagSet_ReleaseValue,
                                       ClientInvalTagSet_CompareValue);
        
bool
Client_InitWith(Client_t *client, ClientConnectCB_t cb, void *cbArg,
                ClientPolicyChoosePin_t *choosePin,
                ClientNode_t *pincushion, int nCacheNodes, ClientNode_t *cacheNodes)
{
        client->cb = cb;
        client->cbArg = cbArg;
        client->pg = NULL;

        client->choosePin = choosePin;

        client->pincushion = pincushion;
        client->cacheNodes = cacheNodes;
        client->nCacheNodes = nCacheNodes;

        client->bypassMode = false;
        client->memcachedMode = false;

        client->rootFrame.client = client;
        client->rootFrame.key = NULL;
        client->rootFrame.keyLen = 0;
        client->rootFrame.parent = NULL;
        client->rootFrame.willingToDealWithInvalidations = false;
        ClientInvalTagSet_Init(&client->rootFrame.invalTags);
        client->bottom = NULL;

        memset(&client->stats, 0, sizeof client->stats);

        if (choosePin)
                Notice("Using pin policy '%s'", choosePin->name);

        return true;
}

bool
Client_Init(Client_t *client, ClientConnectCB_t cb, void *cbArg)
{
        ClientPolicyChoosePin_t *choosePin = NULL;
        ClientNode_t *pincushion = NULL;
        ClientNode_t *cacheNodes = NULL;
        int nCacheNodes = 0;
        int maxCacheNodes = 0;
        FILE *nodesFile = NULL;
        bool result = false;

        nodesFile = fopen("nodes.txt", "r");
        if (!nodesFile) {
                PWarning("Could not open nodes.txt");
                goto end;
        }

        int lineno = 0;

        while (1) {
                char line[128];
                if (!fgets(line, sizeof line, nodesFile)) {
                        if (ferror(nodesFile)) {
                                Warning("I/O error reading nodes file");
                                goto end;
                        }
                        break;
                }
                ++lineno;

                char *p;

                // Remove any comments
                p = strchr(line, '#');
                if (p)
                        p = '\0';

                // Remove trailing whitespace
                p = line + strlen(line);
                while (p > line && isspace(*(p-1)))
                       --p;
                *p = '\0';

                // Skip to the next line if this one was all
                // whitespace and/or comments
                if (p == line)
                        continue;

                // Skip leading whitespace
                p = line;
                while (isspace(*p))
                        ++p;

                // The first line gives the pin policy
                if (!choosePin) {
                        choosePin = Client_LookupPolicy(p);
                        if (!choosePin)
                                Panic("Unknown pin policy '%s'", p);
                        continue;
                }

                // The remaining lines give host names
                char *hostname = p;

                p = strchr(p, ':');
                if (!p) {
                        Warning("Malformed nodes.txt (line %d)", lineno);
                        goto end;
                }
                *(p++) = '\0';
                char *port = p;
                if (!*p) {
                        Warning("Malformed nodes.txt (line %d)", lineno);
                        goto end;
                }

                ClientNode_t *node;
                if (strlen(hostname) + 1 > sizeof node->hostname ||
                    strlen(port) + 1 > sizeof node->port) {
                        Warning("Malformed nodes.txt (line %d)", lineno);
                        goto end;
                }

                // The pin cushion is the first hostname.  The
                // remaining ones are cache nodes
                if (!pincushion) {
                        pincushion = malloc(sizeof *pincushion);
                        if (!pincushion) {
                                Warning("Failed to allocate pin cushion host");
                                goto end;
                        }
                        node = pincushion;
                } else {
                        if (nCacheNodes == maxCacheNodes) {
                                if (maxCacheNodes == 0)
                                        maxCacheNodes = 8;
                                else
                                        maxCacheNodes *= 2;

                                void *newNodes = realloc(cacheNodes,
                                                         maxCacheNodes *
                                                         sizeof *cacheNodes);
                                if (!newNodes) {
                                        Warning("Failed to allocate cache nodes vector");
                                        goto end;
                                }
                                cacheNodes = newNodes;
                        }
                        node = &cacheNodes[nCacheNodes];
                        ++nCacheNodes;
                }

                strcpy(node->hostname, hostname);
                strcpy(node->port, port);
                node->host = HOST_NULL;
        }

        if (!Client_InitWith(client, cb, cbArg, choosePin, pincushion,
                             nCacheNodes, cacheNodes))
                goto end;

        result = true;

end:
        if (nodesFile)
                fclose(nodesFile);
        if (!result) {
                // The easiest way to clean up is to use release.
                // InitWith is okay with NULL pointers.
                Client_InitWith(client, cb, cbArg, choosePin, pincushion,
                                nCacheNodes, cacheNodes);
                Client_Release(client);
        }
        return result;
}

void
Client_Release(Client_t *client)
{
        if (client->bottom)
                Panic("Transaction still in progress");

        if (client->cacheNodes) {
                for (int i = 0; i < client->nCacheNodes; ++i)
                        RPC_Release(&client->cacheNodes[i].host);
                free(client->cacheNodes);
                client->cacheNodes = NULL;
        }
        if (client->pincushion) {
                RPC_Release(&client->pincushion->host);
                free(client->pincushion);
                client->pincushion = NULL;
        }
}

bool
Client_ConnectPG(Client_t *client)
{
        if (client->pg)
                return true;

        Debug("Acquiring Postgres connection");
        client->pg = client->cb(client, client->cbArg);
        if (!client->pg) {
                Debug("Failed to acquire Postgres connection");
                return false;
        }
        return true;
}

bool
Client_ReleasePG(Client_t *client)
{
        if (client->bottom)
                Panic("Transaction still in progress");

        client->pg = NULL;
        return true;
}

void
Client_SetBypass(Client_t *client, bool bypass)
{
        client->bypassMode = bypass;
}

void
Client_SetMemcached(Client_t *client, bool memcachedMode)
{
        client->memcachedMode = memcachedMode;
}

static host_t *
NodeToHost(ClientNode_t *node)
{
        if (!RPC_IsValid(&node->host)) {
                Latency_Start(&connectLatency);
                if (!RPCC_Connect(node->hostname, node->port, &node->host)) {
                        Latency_EndType(&connectLatency, '!');
                        Warning("Failed to connect to host %s:%s",
                                node->hostname, node->port);
                        return NULL;
                }
                Latency_End(&connectLatency);
                Assert(RPC_IsValid(&node->host));
        }
        return &node->host;
}

static ClientNode_t *
Client_GetNode(Client_t *client, const void *key, size_t keyLen)
{
        Assert(!client->bypassMode);
        int n = hash(key, keyLen, 0x46db2f8d) % client->nCacheNodes;
        return &client->cacheNodes[n];
}

bool
Client_BeginRO(Client_t *client, double freshness)
{
        if (client->bottom) {
                Warning("Transaction already running");
                return false;
        }

        if (client->bypassMode) {
                if (!PinSet_Init(&client->pinSet, NULL, 0, true)) {
                        Warning("Failed to initialize pin set");
                        return false;
                }
        } else {
                host_t *pincushion = NodeToHost(client->pincushion);
                if (!pincushion) {
                        Warning("Failed to connect to pin cushion");
                        // XXX We could limp
                        return false;
                }
                uint32_t nPins;
                Latency_Start(&pincushionRequestLatency);
                PinStamp_t *pins =
                        PincushionProxy_Request(pincushion,
                                                Timeval_FromSecs(freshness),
                                                &nPins);
                Latency_End(&pincushionRequestLatency);
                if (!PinSet_Init(&client->pinSet, pins, nPins, true)) {
                        Warning("Failed to initialize pin set");
                        pincushion = NodeToHost(client->pincushion);
                        if (pincushion)
                                PincushionProxy_Release(pincushion);
                        return false;
                }
        }

        client->activePin = PIN_INVALID;

        if (PinSet_JustStar(&client->pinSet)) {
            client->earliestPin = PIN_INVALID;
        } else {
            client->earliestPin = PinSetFirstPin(&client->pinSet)->pin;
        }

        client->rootFrame.interval.lower = PIN_NEG_INF;
        client->rootFrame.interval.upper = PIN_INF;
        client->rootFrame.interval.stillValid = true;
        if (client->memcachedMode) {
                if (!PinSet_JustStar(&client->pinSet))
                        client->rootFrame.interval =
                                PinSet_BoundsWithoutStar(&client->pinSet);
                else
                        client->rootFrame.interval.upper = PIN_MIN;
        }
        
        client->rootFrame.willingToDealWithInvalidations = false;
        ClientInvalTagSet_Init(&client->rootFrame.invalTags);
        client->bottom = &client->rootFrame;

        Debug("Began cache transaction with pin set " FMT_PINSET,
              XVA_PINSET(&client->pinSet));

        ++client->stats.roCacheXactions;
        return true;
}

bool
Client_Commit(Client_t *client)
{
        if (!client->bottom) {
                Warning("No read-only transaction running");
                return false;
        }
        if (client->bottom != &client->rootFrame) {
                Warning("Attempted to commit from within a cacheable function");
                return false;
        }

        client->bottom = NULL;

        bool result = true;
        if (client->activePin != PIN_INVALID) {
                Debug("RO query COMMIT");
                Assert(client->pg);
                Latency_Start(&dbCommitLatency);
                PGresult *res = PQexec(client->pg, "COMMIT");
                if (PQresultStatus(res) != PGRES_COMMAND_OK) {
                        Latency_EndType(&dbCommitLatency, '!');
                        Warning("Commit failed: %s", PQerrorMessage(client->pg));
                        result = false;
                        // XXX Should we release the pin or not?
                } else {
                        Latency_End(&dbCommitLatency);
                }
                PQclear(res);
                client->activePin = PIN_INVALID;
                Debug("Committed underlying transaction");
        }

        if (!client->bypassMode) {
                host_t *pincushion = NodeToHost(client->pincushion);
                if (!pincushion)
                        Warning("Failed to release pin from pin cushion");
                else {
                        Latency_Start(&pincushionReleaseLatency);
                        PincushionProxy_Release(pincushion);
                        Latency_End(&pincushionReleaseLatency);
                }
        }

        Debug("Committed cache transaction");

        return result;
}

static PGresult *
Client_ExecConsumeOne(Client_t *client, const char *cmd,
                      const char *expect1, const char *expect2)
{
        PGresult *res = PQgetResult(client->pg);
        if (PQresultStatus(res) != PGRES_COMMAND_OK) {
                Warning("%s failed: %s", cmd, PQerrorMessage(client->pg));
                return res;
        }
        char *status = PQcmdStatus(res);
        if (!(strncmp(status, expect1, strlen(expect1)) == 0 ||
              (expect2 && strncmp(status, expect2, strlen(expect2)) == 0))) {
                Warning("%s gave unexpected status: %s", cmd, status);
                PQclear(res);
                return PQmakeEmptyPGresult(client->pg,
                                           PGRES_BAD_RESPONSE);
        }
        PQclear(res);
        return NULL;
}

static void
Client_UpdateInterval(Client_t *client, interval_t interval)
{
        if (client->memcachedMode)
        {
                if ((interval.upper > client->bottom->interval.upper)
                    || (client->bottom->interval.upper == PIN_INF))
                        client->bottom->interval.upper = interval.upper;
                
                if (client->bottom->interval.stillValid)
                        client->bottom->interval.stillValid =
                                interval.stillValid;
                return;
        }
        
        if (client->bypassMode)
                return;

        client->bottom->interval =
                Interval_Intersect(client->bottom->interval, interval,
                                   PIN_NEG_INF);
        PinSet_IntersectWith(&client->pinSet, interval);
}

static void
Client_AddInvalTag(Client_t *client, const char *tag)
{
        Assert(tag != NULL);
        
        ClientInvalTag_t tagStruct;
        ClientInvalTag_t *existingTag, *newTag;
        
        tagStruct.tag = malloc(strlen(tag)+1);
        Assert(tagStruct.tag != NULL);
        strcpy(tagStruct.tag, tag);

        /*
         * Check for existing tag. ClientInvalTagSet_Insert *should*
         * disallow duplicate, but just to be sure...
         */
        existingTag = ClientInvalTagSet_Find(&client->bottom->invalTags,
                                             tagStruct,
                                             ClientInvalTagSet_EQ);
        if (existingTag != NULL)
                return;

        newTag = ClientInvalTagSet_Insert(&client->bottom->invalTags,
                                          tagStruct);
        Assert(newTag != NULL);
}

static void
Client_UpdateInvalTagsFromSet(Client_t *client,
                               ClientInvalTagSet_t *tagSet)
{
        if (ClientInvalTagSet_Size(tagSet) == 0)
                return;
        
        ClientInvalTagSet_Iterator_t *iter =
                ClientInvalTagSet_First(tagSet);

        do
        {
                ClientInvalTag_t *tag =
                        ClientInvalTagSet_Iterator_Value(iter);
                Client_AddInvalTag(client, tag->tag);
        } while (ClientInvalTagSet_Iterator_Next(iter));
        ClientInvalTagSet_Iterator_Release(iter); 
}

/**
 * Based on PQexecFinish.
 */
static PGresult *
Client_ExecFinish(PGconn *conn)
{
        PGresult   *result;
        PGresult   *lastResult;

        lastResult = NULL;
        while ((result = PQgetResult(conn)) != NULL)
        {
                if (lastResult) {
                        // PQexec goes to the trouble of concatenating
                        // fatal errors, but 29.3.1 suggests that it
                        // stops processing at the first failed
                        // command.
                        Assert(PQresultStatus(lastResult) != PGRES_FATAL_ERROR);
                        PQclear(lastResult);
                }
                lastResult = result;
                ExecStatusType status = PQresultStatus(result);
                if (status == PGRES_COPY_IN ||
                    status == PGRES_COPY_OUT ||
                    PQstatus(conn) == CONNECTION_BAD)
                        break;
        }

        return lastResult;
}

static inline bool
Client_ResultOkay(PGresult *res)
{
        ExecStatusType status = PQresultStatus(res);
        return !(status == PGRES_BAD_RESPONSE ||
                 status == PGRES_NONFATAL_ERROR ||
                 status == PGRES_FATAL_ERROR);
}

PGresult *
Client_Exec(Client_t *client, const char *sql)
{
        if (!Client_ConnectPG(client)) {
                // We can't even create a PGresult without a PGconn.
                // We're screwed.
                Panic("Failed to AcquirePG");
        }

        if (!client->bottom) {
                // Just pass the query through
                Debug("RW query %s", sql);
                ++client->stats.rwQueries;
                Latency_Start(&rwQueryLatency);
                PGresult *res = PQexec(client->pg, sql);
                if (Client_ResultOkay(res))
                        Latency_End(&rwQueryLatency);
                else
                        Latency_EndType(&rwQueryLatency, '!');
                return res;
        }

        pin_t pin = client->choosePin->fn(&client->pinSet);
        Assert(pin != PIN_INVALID);

        char *newSql = NULL;
        bool needCommit = false;
        bool needBegin  = false;
        bool needNewPin = false;
        if (pin != client->activePin) {
                if (!PinSet_Contains(&client->pinSet, pin))
                        Panic("Pin set %s does not contain chosen pin " FMT_PIN,
                              Message_DFree(PinSet_Fmt(&client->pinSet)),
                              VA_PIN(pin));

                // Figure out what we need to do
                if (client->activePin != PIN_INVALID)
                        needCommit = true;
                needBegin = true;
                if (pin == PIN_INF && !client->bypassMode)
                        needNewPin = true;

                // Allocate enough space for the SQL we have to issue
                const char *cmdCommit = "COMMIT; ";
                const char *cmdBeginPresent = "BEGIN READ ONLY; PIN; ";
                const char *cmdBeginPresentBypass = "BEGIN READ ONLY; ";
                const char *cmdBeginAt = "BEGIN READ ONLY SNAPSHOTID %d; ";
                size_t newSqlSize = 0;
                if (needCommit)
                        newSqlSize += strlen(cmdCommit);
                if (needNewPin)
                        newSqlSize += strlen(cmdBeginPresent);
                else if (client->bypassMode)
                        newSqlSize += strlen(cmdBeginPresentBypass);
                else
                        newSqlSize += strlen(cmdBeginAt) + 20;
                newSqlSize += strlen(sql);
                newSql = malloc(newSqlSize + 1);
                if (!newSql)
                        Panic("Unable to allocate %ld bytes for SQL command",
                              (long)(newSqlSize + 1));

                // Build up the new command
                newSql[0] = '\0';
                if (needCommit)
                        strcat(newSql, cmdCommit);
                if (needNewPin)
                        strcat(newSql, cmdBeginPresent);
                else if (client->bypassMode)
                        strcat(newSql, cmdBeginPresentBypass);
                else
                        sprintf(newSql + strlen(newSql), cmdBeginAt, pin);
                strcat(newSql, sql);
        }

        // Use the asynchronous interface to get the results of the
        // other commands we need to send.  In addition to saving a
        // round trip to the database, this allows us to overlap
        // talking to the pin cushion with the database processing the
        // real query.

        // XXX If we really want to be like PQexec, do
        // PQexecStart here.

        PGresult *res = NULL;   // Initialize to placate GCC

        Debug("RO query %s", newSql ? newSql : sql);
        ++client->stats.roQueries;
        Latency_Start(&roQueryLatency);
        if (!PQsendQuery(client->pg, newSql ? newSql : sql)) {
                free(newSql);
                res = NULL;
                goto end;
        }
        free(newSql);

        // Get COMMIT result
        if (needCommit) {
                res = Client_ExecConsumeOne(client, "COMMIT",
                                            "COMMIT", "ROLLBACK");
                if (res)
                        goto end;
                Debug("Committed old underlying transaction");
        }

        // Get BEGIN result
        if (needBegin) {
                res = Client_ExecConsumeOne(client, "BEGIN", "BEGIN", NULL);
                if (res)
                        goto end;
                Debug("Began new underlying transaction");
                ++client->stats.roDBXactions;
        }

        // Get PIN result
        if (needNewPin) {
                PGresult *res = PQgetResult(client->pg);
                if (PQresultStatus(res) != PGRES_COMMAND_OK) {
                        Warning("PIN failed: %s", PQerrorMessage(client->pg));
                        goto end;
                }

                // Parse the pin result
                char *status = PQcmdStatus(res);
                long long tssec, tsusec;
                if (sscanf(status, "PIN %u %lld %lld",
                           &pin, &tssec, &tsusec) != 3) {
                        Warning("PIN gave unexpected status: %s", status);
                        PQclear(res);
                        res = PQmakeEmptyPGresult(client->pg,
                                                  PGRES_BAD_RESPONSE);
                        goto end;
                }
                PQclear(res);
                PinStamp_t pinstamp = {pin, {.tv_sec = tssec,
                                             .tv_usec = tsusec}};
                Debug("Pinned snapshot " FMT_PIN " at " FMT_TIMEVAL_ABS,
                      VA_PIN(pinstamp.pin),
                      XVA_TIMEVAL_ABS(pinstamp.tv));

                // Update our pin set
                PinSet_ReifyStar(&client->pinSet, pinstamp);
                if (client->earliestPin == PIN_INVALID)
                        client->earliestPin = pinstamp.pin;
                if (client->memcachedMode)
                {
                        if (client->bottom->interval.upper < pin)
                                client->bottom->interval.upper = pin;
                }
                Debug("Pin set reified to %s",
                      Message_DFree(PinSet_Fmt(&client->pinSet)));

                // Put the pin in the pin cushion
                host_t *pincushion = NodeToHost(client->pincushion);
                if (!pincushion)
                        Warning("Failed to insert pin into pin cushion");
                else {
                        Latency_Start(&pincushionInsertLatency);
                        PincushionProxy_Insert(pincushion, pinstamp);
                        Latency_End(&pincushionInsertLatency);
                }
        }

        // Update our active pin (we have to do this after we get a
        // new pin, if we needed one)
        if (needBegin)
                client->activePin = pin;
        Assert(!client->bypassMode || client->activePin == PIN_INF);

        // Consume everything else, like PQexec
        res = Client_ExecFinish(client->pg);

        // Get the validity interval out of the database
        if (!client->bypassMode &&
            (PQresultStatus(res) == PGRES_TUPLES_OK)) {
                char *status = PQcmdStatus(res);
                interval_t interval;
                int found;
                int nTags = 0;
                int n;
                int i = 0;
                char *tag;
                Debug("Database status: %s", status);
                interval.stillValid = false;
                found = sscanf(status, "SELECT VALIDITY %u %u TAGS %u %n",
                               &interval.lower, &interval.upper, &nTags, &n);
                if (found < 2) {
                        Warning("Expected validity interval, got %s", status);
                } else {
                        if (found == 3) {
                                // We got invalidation tags...
                                // ...so the interval is still valid
                                interval.stillValid = true;
                                Assert(nTags > 0);

                                // ...and we need to parse the tags
                                status += n;
                                while (status != NULL) {
                                        tag = strsep(&status, " ");
                                        Client_AddInvalTag(client, tag);
                                        i++;
                                }
                                Assert(i == nTags);
                                
                        }
                        Client_UpdateInterval(client, interval);
                }
        }

end:
        if (Client_ResultOkay(res))
                Latency_End(&roQueryLatency);
        else
                Latency_EndType(&roQueryLatency, '!');
        return res;
}

bool
Client_MightCache(Client_t *client)
{
        return client->bottom != NULL;
}

void *
Client_TryEnterCacheable(Client_t *client, const void *key, size_t keyLen,
                         size_t *dataLenOut, ClientCacheableFrame_t *frameOut)
{
        if (!client->bottom) {
                // The caller should have checked Client_MightCache
                // first, in order to avoid serializing the key.
                Panic("No read-only transaction running");
        }

        Debug("Trying to enter cacheable with pin set %s",
              Message_DFree(PinSet_Fmt(&client->pinSet)));

        if (!PinSet_JustStar(&client->pinSet)) {
                // Connect to the cache
                ClientNode_t *node = Client_GetNode(client, key, keyLen);
                host_t *cacheHost = NodeToHost(node);
                if (!cacheHost) {
                        // XXX Should we cut our losses and tell the
                        // caller to not bother capturing and
                        // serializing the result?
                        Warning("Failed to connect to cache node");
                } else {
                        // Look up key in the cache
                        interval_t lookupInterval;
                        lookupInterval =
                                PinSet_BoundsWithoutStar(&client->pinSet);
                        Debug("Looking up " FMT_VBLOB " over " FMT_INTERVAL,
                              XVA_VBLOB(key, keyLen), VA_INTERVAL(lookupInterval));
                        int nInvalTags;
                        char **invalTags;
                        void *data = ServerProxy_Lookup(cacheHost, key, keyLen,
                                                        lookupInterval,
                                                        client->earliestPin,
                                                        (client->memcachedMode ?
                                                         LOOKUP_POLICY_SCREW_CONSISTENCY :
                                                         LOOKUP_POLICY_DEFAULT),
                                                        dataLenOut,
                                                        &lookupInterval,
                                                        &nInvalTags,
                                                        &invalTags);
                        if (data) {
                                // Cache hit
                                Debug("=> Found " FMT_VBLOB " over " FMT_INTERVAL,
                                      XVA_VBLOB(data, *dataLenOut),
                                      VA_INTERVAL(lookupInterval));
                                ++client->stats.cacheHits;

                                // Add any invaltags from the cache
                                for (int i = 0; i < nInvalTags; i++) {
                                        Client_AddInvalTag(client,
                                                           invalTags[i]);
                                        free(invalTags[i]);
                                }
                                if (nInvalTags > 0)
                                        free(invalTags);
                        
                                Client_UpdateInterval(client, lookupInterval);
                                return data;
                        }
                        Debug("=> Not found");
                        ++client->stats.cacheMisses;
                }
        } else {
                ++client->stats.cacheSkips;
        }

        // Either we missed in the cache, our pin set is {*} so we
        // have to run in the present, or we couldn't contact the
        // cache.

        // We start the transaction lazily in Client_Exec.  Here we
        // just set up our frame.  This is particularly useful if our
        // pin set shrinks by calls to other cacheable functions
        // before we actually make a query from this one.
        frameOut->client = client;
        frameOut->key = key;
        frameOut->keyLen = keyLen;
        frameOut->interval.lower = PIN_NEG_INF;
        frameOut->interval.upper = PIN_INF;
        frameOut->interval.stillValid = true;
        frameOut->willingToDealWithInvalidations = false;
        ClientInvalTagSet_Init(&frameOut->invalTags);        
        frameOut->parent = client->bottom;
        client->bottom = frameOut;
        return NULL;
}

static Client_t *
Client_Pop(ClientCacheableFrame_t *frame)
{
        Client_t *client = frame->client;

        if (!client->bottom) {
                // This _really_ shouldn't happen because
                // Client_TryEnterCacheable should have caught this.
                Panic("No read-only transaction running");
        }

        if (client->bottom != frame)
                Panic("Exit frame doesn't match bottom of stack");

        if (!client->bottom->parent)
                Panic("Already at top of stack");

        // Pop our frame
        client->bottom = frame->parent;

        return client;
}

void
Client_ExitCacheable(ClientCacheableFrame_t *frame,
                     const void *data, size_t dataLen)
{
        Client_t *client = Client_Pop(frame);
        interval_t itemInterval;
        const char **invalTags = NULL;
        int nTags = 0;
        Debug("Exiting cacheable function with key " FMT_VBLOB,
              XVA_VBLOB(frame->key, frame->keyLen));
        
        // Update parents' interval and inval tag set. Do *not* update
        // parent's willingToDealWithInvalidations flag, as this is a
        // per-frame property.
        Client_UpdateInterval(client, frame->interval);

        Client_UpdateInvalTagsFromSet(client, &frame->invalTags);
        
        itemInterval = frame->interval;
        
        if (itemInterval.stillValid &&
            !client->bypassMode)
        {
                // Explicit invalidations: clear still-valid flag if no
                // invalidation tags were provided
                if (ClientInvalTagSet_Size(&frame->invalTags) == 0)
                        Assert(!frame->willingToDealWithInvalidations);

                if (!frame->willingToDealWithInvalidations)
                {
                        itemInterval.stillValid = false;
                }
                else
                {
//                        Warning("Marshalling invalTag set, size %zd",
//                                ClientInvalTagSet_Size(&frame->invalTags));
                
                        invalTags = malloc(sizeof(const char *) *
                                           ClientInvalTagSet_Size(&frame->invalTags));
                        if (invalTags == NULL)
                                Panic("Failed to allocate memory for invalidation tags");

                        ClientInvalTagSet_Iterator_t *iter =
                                ClientInvalTagSet_First(&frame->invalTags);
                        do
                        {
                                ClientInvalTag_t *tag =
                                        ClientInvalTagSet_Iterator_Value(iter);
//                                Warning("nTags %d tag %s", nTags, tag->tag);
                                invalTags[nTags] = tag->tag;
                                nTags++;
                        } while (ClientInvalTagSet_Iterator_Next(iter));
                        ClientInvalTagSet_Iterator_Release(iter);
//                        Warning("nTags %d, size %zd", nTags, ClientInvalTagSet_Size(&frame->invalTags));
                        Assert(nTags ==
                               ClientInvalTagSet_Size(&frame->invalTags));
//                        Notice("Exiting cacheable function with key " FMT_VBLOB
//                               "interval " FMT_INTERVAL " nTags %d tag0 %s",
//                               XVA_VBLOB(frame->key, frame->keyLen),
//                               VA_INTERVAL(itemInterval), nTags, invalTags[0]);
                }                
        }

        
        // Update the cache
        if (!client->bypassMode) {
                ClientNode_t *node = Client_GetNode(frame->client,
                                                    frame->key, frame->keyLen);
                host_t *cacheHost = NodeToHost(node);
                if (!cacheHost) {
                        Warning("Failed to connect to cache node");
                } else {
                        ServerProxy_Put(cacheHost, frame->key, frame->keyLen,
                                        itemInterval, data, dataLen,
                                        nTags, invalTags,
                                        client->memcachedMode);
                }
        }

        ClientInvalTagSet_Release(&frame->invalTags);
}

void
Client_ExitCacheableAbort(ClientCacheableFrame_t *frame)
{
        Debug("Aborting cacheable function with key " FMT_VBLOB,
              XVA_VBLOB(frame->key, frame->keyLen));
        Client_Pop(frame)->stats.cacheableAborts++;
}

void
Client_DumpStats(Client_t *client)
{
        time_t t;
        char buf[32];
        t = time(NULL);
        ctime_r(&t, buf);
        Notice("Client statistics as of %s", buf);
#define PR(f) QNotice("%s %lld", #f, client->stats.f);
        CLIENT_STATS_LL_FIELDS(PR);
#undef PR
#define A(f) , client->stats.f
        QNotice("STATLINE %lld %lld %lld %lld %lld %lld %lld %lld"
                CLIENT_STATS_LL_FIELDS(A));
#undef A
}

void
Client_AddExplicitInvalTag(Client_t *client, const char *tag)
{
        if (!client->bottom) {
            // No read-only transaction running. Silently bail out.
            // This might happen if invoking a cacheable function
            // (which won't be cached) in a RW transaction.
            return;
        }

        Client_AddInvalTag(client, tag);
}

void
Client_ExplicitlyInvalidate(Client_t *client, const char *tag)
{
        if (client->bottom != NULL)
                Panic("Attempted to perform an explicit invalidation "
                      "in a read-only transaction");

        Debug("Explicit invalidation for %s", tag);

        if (!Client_ConnectPG(client))
                Panic("Failed to AcquirePG");

        char *sql;
        if (asprintf(&sql, "INVALIDATE \"%s\";", tag) == -1)
                Panic("Failed to alloc buffer for invalidate statement");
        
        ++client->stats.rwQueries;
        Latency_Start(&rwQueryLatency);
        PGresult *res = PQexec(client->pg, sql);
        if (Client_ResultOkay(res))
                Latency_End(&rwQueryLatency);
        else
                Panic("Failed to perform invalidation for %s: %s",
                      tag, PQresultErrorMessage(res));
        
        free(sql);
        
}

static ClientPolicyChoosePin_t *policyHead;

void
_Client_RegisterPolicy(ClientPolicyChoosePin_t *policy)
{
        if (policy->next)
                Panic("Choose-pin policy '%s' registered twice",
                      policy->name);
        policy->next = policyHead;
        policyHead = policy;
}

ClientPolicyChoosePin_t *
Client_LookupPolicy(const char *name)
{
        ClientPolicyChoosePin_t *p;
        for (p = policyHead; p; p = p->next)
                if (strcmp(name, p->name) == 0)
                        return p;
        return NULL;
}
