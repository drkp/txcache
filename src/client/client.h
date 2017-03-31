// -*- c-file-style: "bsd" -*-

#ifndef _CLIENT_CLIENT_H_
#define _CLIENT_CLIENT_H_

#include <libpq-fe.h>
#include <stdbool.h>

#include "lib/rpc.h"
#include "lib/interval.h"
#include "lib/pinset.h"
#include "lib/tmpl/rbtree.h"


typedef struct ClientInvalTag_t
{
        char *tag;
} ClientInvalTag_t;

%instance RBTree ClientInvalTagSet(ClientInvalTag_t, ClientInvalTag_t);

typedef struct ClientCacheableFrame_t
{
        struct Client_t *client;
        const void *key;
        size_t keyLen;
        interval_t interval;
        ClientInvalTagSet_t invalTags;
        bool willingToDealWithInvalidations;
        struct ClientCacheableFrame_t *parent;
} ClientCacheableFrame_t;

typedef struct ClientPolicyChoosePin_t
{
        pin_t (*fn)(PinSet_t *);
        const char *name;
        struct ClientPolicyChoosePin_t *next;
} ClientPolicyChoosePin_t;

#define CLIENT_REGISTER_POLICY(policy)                                  \
        static __attribute__((constructor)) void _##policy##_register(void) \
        {                                                               \
                _Client_RegisterPolicy(&policy);                        \
        }

typedef struct ClientNode_t
{
        char hostname[32];
        char port[16];
        host_t host;
} ClientNode_t;

#define CLIENT_STATS_LL_FIELDS(X)               \
        X(roQueries)                            \
        X(rwQueries)                            \
        X(roCacheXactions)                      \
        X(roDBXactions)                         \
        X(cacheHits)                            \
        X(cacheMisses)                          \
        X(cacheSkips)                           \
        X(cacheableAborts)

typedef struct ClientStats_t
{
#define DEF(n) long long n;
        CLIENT_STATS_LL_FIELDS(DEF)
#undef DEF
} ClientStats_t;

struct Client_t;
typedef PGconn *(*ClientConnectCB_t)(struct Client_t *, void *);

typedef struct Client_t
{
        // Postgres connection
        ClientConnectCB_t cb;
        void *cbArg;
        PGconn *pg;

        // Policy
        ClientPolicyChoosePin_t *choosePin;

        // Cache system connections
        ClientNode_t *pincushion;
        ClientNode_t *cacheNodes;
        int nCacheNodes;

        // Transaction state
        bool bypassMode;
        bool memcachedMode;
        PinSet_t pinSet;
        pin_t activePin;
        pin_t earliestPin;      /* for stat purposes only */
        struct ClientCacheableFrame_t rootFrame;

        // Cacheable function stack
        struct ClientCacheableFrame_t *bottom;

        // Statistics
        ClientStats_t stats;
} Client_t;

bool Client_InitWith(Client_t *client, ClientConnectCB_t cb, void *cbArg,
                     ClientPolicyChoosePin_t *choosePin,
                     ClientNode_t *pincushion,
                     int nCacheNodes, ClientNode_t *cacheNodes);
bool Client_Init(Client_t *client, ClientConnectCB_t cb, void *cbArg);
void Client_Release(Client_t *client);
bool Client_ConnectPG(Client_t *client);
bool Client_ReleasePG(Client_t *client);
void Client_SetBypass(Client_t *client, bool bypass);
void Client_SetMemcached(Client_t *client, bool memcached);
bool Client_BeginRO(Client_t *client, double freshness);
bool Client_Commit(Client_t *client);
PGresult *Client_Exec(Client_t *client, const char *sql);
bool Client_MightCache(Client_t *client);
void *Client_TryEnterCacheable(Client_t *client, const void *key, size_t keyLen,
                               size_t *dataLenOut,
                               ClientCacheableFrame_t *frameOut);
void Client_ExitCacheable(ClientCacheableFrame_t *frame,
                         const void *data, size_t dataLen);
void Client_ExitCacheableAbort(ClientCacheableFrame_t *frame);
void Client_DumpStats(Client_t *client);
void Client_AddExplicitInvalTag(Client_t *client, const char *tag);
void Client_ExplicitlyInvalidate(Client_t *client, const char *tag);

void _Client_RegisterPolicy(ClientPolicyChoosePin_t *policy);
ClientPolicyChoosePin_t *Client_LookupPolicy(const char *name);

#endif // _CLIENT_CLIENT_H_
