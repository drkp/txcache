// -*- c-file-style: "bsd" -*-
#ifndef _LIB_TMPL_HASHTABLE_IMPL_H_
#define _LIB_TMPL_HASHTABLE_IMPL_H_

#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "lib/hash.h"
#include "lib/message.h"
#include "lib/memory.h"

#define INITIAL_POWER 10
#define LOAD_FACTOR 0.75

#ifndef PARANOID
#define PARANOID 0
#endif

%template HashtableImpl HT(VALUE_t, FIELD);

static void HT_Expand(HT_t *ht);

bool
HT_Init(HT_t *ht)
{
        ht->power = INITIAL_POWER;
        ht->count = 0;
        ht->buckets = calloc(hashsize(ht->power),
                             sizeof *ht->buckets);
        if (!ht->buckets)
                return false;
        return true;
}

void
HT_Release(HT_t *ht)
{
        if (ht->count)
                Panic("Cannot release non-empty hash table");
        if (ht->buckets) {
                // Free bucket array
                free(ht->buckets);
                ht->buckets = NULL;
        }
        ht->count = 0;
}

static VALUE_t **
HT_GetBucket(HT_t *ht, const void *key, size_t keyLen)
{
        uint32_t seed = (uint32_t)(long)ht;
        int nBucket = hash(key, keyLen, seed) & hashmask(ht->power);
        return ht->buckets + nBucket;
}

static bool
HT_KeyCmp(VALUE_t *link, const void *key, size_t keyLen)
{
        return (link->FIELD.keyLen == keyLen &&
                memcmp(link->FIELD.key, key, keyLen) == 0);
}

VALUE_t *
HT_Get(HT_t *ht, const void *key, size_t keyLen)
{
        // Follow the chain
        VALUE_t *link = *HT_GetBucket(ht, key, keyLen);
        for (; link; link = link->FIELD.next) {
                if (HT_KeyCmp(link, key, keyLen)) {
                        // Found it
                        return link;
                }
        }
        return NULL;
}

void
HT_Insert(HT_t *ht, VALUE_t *value, const void *key, size_t keyLen)
{
        VALUE_t **bucket = HT_GetBucket(ht, key, keyLen);

        // Check if it exists
        if (PARANOID) {
                VALUE_t *link;
                for (link = *bucket; link; link = link->FIELD.next) {
                        if (HT_KeyCmp(link, key, keyLen)) {
                                Panic("Hashtable already contains key "
                                      FMT_VBLOB, XVA_VBLOB(key, keyLen));
                        }
                }
        }

        // Link the value in
        value->FIELD.key = key;
        value->FIELD.keyLen = keyLen;
        if (*bucket)
                (*bucket)->FIELD.pprev = &value->FIELD.next;
        value->FIELD.next = *bucket;
        value->FIELD.pprev = bucket;
        *bucket = value;
        ++ht->count;

        // Do we need to expand?
        if (hashsize(ht->power) * LOAD_FACTOR < ht->count) {
                HT_Expand(ht);
        }
}

void
HT_Unlink(HT_t *ht, VALUE_t *value)
{
        *value->FIELD.pprev = value->FIELD.next;
        --ht->count;
}

static void
HT_Expand(HT_t *ht)
{
        VALUE_t **old = ht->buckets;
        VALUE_t **new = calloc(hashsize(ht->power + 1),
                               sizeof *ht->buckets);
        if (!new)
                Panic("Failed to allocate expanded hash table");

        ++ht->power;
        ht->buckets = new;

        for (int i = 0; i < hashsize(ht->power - 1); ++i) {
                VALUE_t *link = old[i];
                while (link) {
                        VALUE_t *next = link->FIELD.next;
                        VALUE_t **bucket =
                                HT_GetBucket(ht, link->FIELD.key, link->FIELD.keyLen);
                        if (*bucket)
                                (*bucket)->FIELD.pprev = &link->FIELD.next;
                        link->FIELD.next = *bucket;
                        link->FIELD.pprev = bucket;
                        *bucket = link;
                        link = next;
                }
        }

        free(old);
}

int
HT_Size(HT_t *ht, size_t *footprint)
{
        if (footprint)
                *footprint = (sizeof *ht +
                              (hashsize(ht->power) * sizeof *ht->buckets) +
                              ht->count * MALLOC_SIZE(sizeof **ht->buckets));
        return ht->count;
}

void
HT_Iter(HT_t *ht, HT_Iter_t *iterOut)
{
        iterOut->ht = ht;
        VALUE_t **bucket = ht->buckets;
        VALUE_t **end = ht->buckets + hashsize(ht->power);
        while (!(*bucket) && bucket < end)
                ++bucket;
        iterOut->bucket = bucket;
        if (bucket == end)
                iterOut->next = NULL;
        else
                iterOut->next = *bucket;
}

VALUE_t *
HT_IterNext(HT_Iter_t *iter)
{
        VALUE_t *res = iter->next;
        if (!res)
                return NULL;

        VALUE_t *next = res->FIELD.next;
        if (!next) {
                VALUE_t **end = iter->ht->buckets + hashsize(iter->ht->power);
                do {
                        ++iter->bucket;
                } while (iter->bucket < end && !(*iter->bucket));
                if (iter->bucket < end)
                        next = *iter->bucket;
        }
        iter->next = next;
        return res;
}

%endtemplate;

#endif // _LIB_TMPL_HASHTABLE_IMPL_H_
