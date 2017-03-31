// -*- c-file-style: "bsd" -*-

#include "storevset.h"

%instance DblListImpl EntryLRU(struct entry_t, lru);
%instance DblListImpl EntryInvTimeOrder(struct entry_t, invTimeOrder);

static inline void
StoreVSet_InitValue(entry_t *entry, pin_t lower)
{
        memset(entry, 0, sizeof *entry);
        entry->interval.lower = lower;
        entry->data = NULL;
        entry->vset = NULL;
        entry->nInvalTags = 0;
        entry->invalTags = NULL;
        entry->invalTagsLen = 0;
}

static inline void
StoreVSet_ReleaseValue(entry_t *entry)
{
#if PARANOID
        assert(entry->lru.list == 0);
#endif
        free(entry->data);
        entry->data = NULL;
        if (entry->nInvalTags > 0)
        {
                /* Assumes that invalTags is allocated as a single
                 * malloc; see Store_CopyInvalTags */
                free(entry->invalTags);
        }
}

static inline int
StoreVSet_CompareValue(entry_t *entry, pin_t lower)
{
        if (entry->interval.lower < lower)
                return -1;
        else if (entry->interval.lower == lower)
                return 0;
        else
                return 1;
}

%instance RBTreeImpl StoreVSetTree(pin_t, entry_t,
                                   StoreVSet_InitValue, StoreVSet_ReleaseValue,
                                   StoreVSet_CompareValue);

%instance HashtableImpl StoreHT(struct StoreVSet_t, linesLink);
