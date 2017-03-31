// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_PROXY_H_
#define _SERVER_PROXY_H_

#include "proto.h"
#include "lookuppolicy.h"
#include "lib/rpc.h"
#include "lib/interval.h"

/**
 * Lookup a entry on a cache node.  If the lookup succeeds, return a
 * copy of the looked-up data, set *dataLenOut to the length of the
 * returned data, and set *intervalOut to the interval of the returned
 * cache entry.  The caller is responsible for freeing the returned
 * buffer.  If the lookup fails for any reason, return NULL.
 */
void *ServerProxy_Lookup(host_t *h, const void *key, size_t keyLen,
                         interval_t interval, pin_t earliestPin,
                         StoreLookupPolicyId_t lookupPolicy,
                         size_t *dataLenOut, interval_t *intervalOut,
                         int *nInvalTagsOut, char ***invalTagsOut);
/**
 * Put an entry on a cache node.  This does not wait for a response
 * (and, thus, cannot fail).  Therefore, there is no guarantee that
 * the entry is successfully added to the cache node.
 */
void ServerProxy_Put(host_t *h, const void *key, size_t keyLen,
                     interval_t interval,
                     const void *data, size_t dataLen,  int nInvalTags,
                     const char * const *invalTags, bool memcachedMode);

/**
 * Invalidate entries on a cache node. Arguments are the timestamp of
 * the invalidation and the list of invalidation tags affected.
 */
void ServerProxy_Invalidate(host_t *h, pin_t pin,
                            int nInvalTags,
                            const char * const *invalTags);

#endif // _SERVER_PROXY_H_
