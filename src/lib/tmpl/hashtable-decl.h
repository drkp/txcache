// -*- c-file-style: "bsd" -*-

#ifndef _LIB_TMPL_HASHTABLE_DECL_H_
#define _LIB_TMPL_HASHTABLE_DECL_H_

%template Hashtable HT(VALUE_t, FIELD);

struct HT_link_t;

typedef struct HT_t
{
        int power;
        int count;
        VALUE_t **buckets;
} HT_t;

typedef struct HT_Link_t
{
        VALUE_t *next;
        VALUE_t **pprev;
        // XXX Do we want to store hash(key) in here instead of
        // recomputing it all over the place?
        const void *key;
        size_t keyLen;
} HT_Link_t;

typedef struct HT_Iter_t
{
        HT_t *ht;
        VALUE_t **bucket;
        VALUE_t *next;
} HT_Iter_t;

/**
 * Initialize the given hash table with the default number of buckets
 * and load factor.
 */
bool HT_Init(HT_t *ht);
/**
 * Release the resources associated with the given hash table.  This
 * implicitly removes all entries from the hash table in the manner of
 * HT_Remove.
 */
void HT_Release(HT_t *ht);
/**
 * Look-up a key in the hash table.  Returns NULL if the key is not
 * found.
 */
VALUE_t *HT_Get(HT_t *ht, const void *key, size_t keyLen);
/**
 * Insert an entry into the hash table and initialize the FIELD field
 * of the given value.  The key must not already be present.  This
 * takes ownership of the key pointer, so pass in a duplicate if
 * necessary.
 */
void HT_Insert(HT_t *ht, VALUE_t *value, const void *key, size_t keyLen);
/**
 * Unlink an entry currently contained in the hash table.  Note that
 * this does not free any resources that may be associated with the
 * value (including the key).
 */
void HT_Unlink(HT_t *ht, VALUE_t *value);
/**
 * Get the number of elements in the hash table, and, optionally, its
 * memory footprint.  Note that the footprint includes all of the
 * element structures, but cannot include and data that may be pointed
 * to by them.
 */
int HT_Size(HT_t *ht, size_t *footprint);

void HT_Iter(HT_t *ht, HT_Iter_t *iterOut);
VALUE_t *HT_IterNext(HT_Iter_t *iter);

%endtemplate;

#endif // _LIB_TMPL_HASHTABLE_DECL_H_
