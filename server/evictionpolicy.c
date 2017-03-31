// -*- c-file-style: "bsd" -*-

#include "store.h"
#include "evictionpolicy.h"


typedef struct LRUEviction_State_t
{
        Store_t *store;
        EntryLRU_t lru;
        entry_t *iterPos;
} LRUEviction_State_t;

static void *
LRUEviction_Init(Store_t *store)
{
        LRUEviction_State_t *state =
                malloc(sizeof(LRUEviction_State_t));
        if (state == NULL)
                Panic("Failed to allocate LRU eviction state");
        EntryLRU_Init(&state->lru);

        return state;
}

static void
LRUEviction_Release(void *cookie)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);
        
        free(state);
}

static void
LRUEviction_Hit(void *cookie, entry_t *entry)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        if (entry == NULL)
                return;

        EntryLRU_Unlink(&state->lru, entry);
        EntryLRU_PushFront(&state->lru, entry);
}

static void
LRUEviction_Put(void *cookie, entry_t *entry, bool extension)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        if (extension)
        {
                // Extended entries are already in the LRU; take them
                // out so we can put them back at the front
                EntryLRU_Unlink(&state->lru, entry);
        }

        EntryLRU_PushFront(&state->lru, entry);
}

static void
LRUEviction_Invalidate(void *cookie, entry_t *entry)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        /* do nothing */
}

static void
LRUEviction_Remove(void *cookie, entry_t *entry)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        EntryLRU_Unlink(&state->lru, entry);
}

static entry_t *
LRUEviction_Evict(void *cookie, size_t bytes)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        /* Evict oldest in LRU */
        return state->lru.tail;
}

static void
LRUEviction_IterReset(void *cookie)
{
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        state->iterPos = state->lru.tail;
}

static entry_t *
LRUEviction_IterNext(void *cookie)
{
        entry_t *r;
        LRUEviction_State_t *state = (LRUEviction_State_t *) cookie;
        Assert(state != NULL);

        r = state->iterPos;

        if (r != NULL)
                state->iterPos = r->lru.prev;

        return r;
}


static
struct StoreEvictionPolicy_t EvictionPolicy_LRU = {
        .name = "LRU",
        .init = LRUEviction_Init,
        .release = LRUEviction_Release,
        .hit = LRUEviction_Hit,
        .put = LRUEviction_Put,
        .invalidate = LRUEviction_Invalidate,
        .remove = LRUEviction_Remove,
        .evict = LRUEviction_Evict,
        .iterReset = LRUEviction_IterReset,
        .iterNext = LRUEviction_IterNext 
};

STORE_REGISTER_EVICTION_POLICY(EvictionPolicy_LRU);
