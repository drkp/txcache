// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_STOREVSET_H_
#define _SERVER_STOREVSET_H_

#include <unistd.h>

#include "lib/interval.h"
#include "lib/tmpl/hashtable.h"
#include "lib/tmpl/rbtree.h"
#include "lib/tmpl/dbllist.h"

struct entry_t;
%instance DblList EntryLRU(struct entry_t, lru);
%instance DblList EntryInvTimeOrder(struct entry_t, invTimeOrder);

// A single entry in the cache.  This represents a single version
// associated with some key over some interval of time.
typedef struct entry_t
{
        interval_t interval;
        void *data;
        size_t dataLen;
        // We need to track the vset that contains this entry so we
        // can remove it when evicting
        struct StoreVSet_t *vset;
        int nInvalTags;
        char **invalTags;
        size_t invalTagsLen;
        EntryLRU_Link_t lru;
        int hitCount; // for statistical purposes
        // We use the following to eliminate any "useless" items that
        // have been invalid for too long
        time_t timeInvalidated;
        EntryInvTimeOrder_Link_t invTimeOrder;
} entry_t;

#define FMT_ENTRY FMT_BLOB FMT_INTERVAL
#define VA_ENTRY(e) VA_BLOB((e)->data, (e)->dataLen), VA_INTERVAL((e)->interval)

%instance RBTree StoreVSetTree(pin_t, entry_t);

%instance Hashtable StoreHT(struct StoreVSet_t, linesLink);

typedef struct StoreVSet_t
{
        StoreVSetTree_t versions;
        StoreHT_Link_t linesLink;
} StoreVSet_t;

#endif // _SERVER_STOREVSET_H_
