// -*- c-file-style: "bsd" -*-

#include "lib/test.h"

#include "../store.h"

#include <stdlib.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

#define DEFAULT_SIZE (1024*1024)
#define N_BIG 1024
#define BIG_LEN (DEFAULT_SIZE/N_BIG)
#define DEFAULT_USELESS_BOUND 86400

Store_t store;

START_TEST(init)
{
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        Store_Release(&store);
}
END_TEST;

#define STORE_LOOKUP(store, key, len, interval) \
    (Store_Lookup(store, key, len, interval, PIN_NEG_INF, LOOKUP_POLICY_LATEST))
#define STORE_PUT(store, key, keyLen, interval, data, datalen, nInvalTags, invalTags) \
    (Store_Put(store, key, keyLen, interval, data, datalen, nInvalTags, invalTags, false))

const char key10[10] = "\x06\0\0\0key10\0";
const char otherkey10[10] = "\x06\0\0\0otr10\0";
const char data10[10] = "\x06\0\0\0dat10\0";
const char otherdata10[10] = "\x06\0\0\0otr10\0";
const char bigdata[BIG_LEN] = {};

interval_t interval(pin_t lower, pin_t upper)
{
        interval_t res = {.lower = lower, .upper = upper,
                          .stillValid = false};
        return res;
}

interval_t unbounded_interval(pin_t lower, pin_t upper)
{
        interval_t res = {.lower = lower, .upper = upper,
                          .stillValid = true};
        return res;
}

void
check_entry(entry_t *e, interval_t interval, const void *data, size_t dataLen)
{
        fail_unless(e != NULL, "Expected some interval, got NULL");
        fail_unless(e->interval.lower == interval.lower &&
                    e->interval.upper == interval.upper,
                    "Expected " FMT_INTERVAL ", got " FMT_INTERVAL,
                    VA_INTERVAL(interval), VA_INTERVAL(e->interval));
        fail_unless(e->dataLen == dataLen,
                    "Expected %d bytes of data, got %d", dataLen, e->dataLen);
        fail_unless(memcmp(e->data, data, dataLen) == 0,
                    "Entry data differs");
}

void
check_no_entry(entry_t *e, interval_t interval)
{
        if (e)
                fail("Expected no entry for " FMT_INTERVAL
                     ", got " FMT_ENTRY,
                     VA_INTERVAL(interval), VA_ENTRY(e));
}

#define ARRAYSIZE(arr) (sizeof(arr)/sizeof((arr)[0]))

START_TEST(lookup_basic)
{
        entry_t *e;
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        fail_unless(STORE_PUT(&store, key10, 10, interval(5, 10), data10, 10,
                              0, NULL),
                    NULL);

        interval_t overlapping[] = {
                // Full overlap
                {5, 10},
                // End points
                {5, 6}, {9, 10},
                // Overlap with ends
                {PIN_MIN, 6}, {9, 15},
        };
        for (int i = 0; i < ARRAYSIZE(overlapping); ++i) {
                e = STORE_LOOKUP(&store, key10, 10, overlapping[i]);
                check_entry(e, interval(5, 10), data10, 10);
        }

        interval_t disjoint[] = {
                {10, 15}, {100, 105},
                {PIN_MIN, 5}, {PIN_MIN, PIN_MIN+1},
        };
        for (int i = 0; i < ARRAYSIZE(disjoint); ++i) {
                e = STORE_LOOKUP(&store, key10, 10, disjoint[i]);
                check_no_entry(e, disjoint[i]);
        }

        Store_Release(&store);
}
END_TEST;

START_TEST(lookup_unbounded)
{
        entry_t *e;
        const char *invalTag = "tag:";
        
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);

        // Set the initial timestamp in the store
        Store_Invalidate(&store, 8, 0, NULL);

        // Shouldn't be able to use an unbounded interval & no tags
        fail_if(STORE_PUT(&store, key10, 10, unbounded_interval(5, 10),
                          data10, 10, 0, NULL),
                "unbounded interval inserted w/o inval tags");

        
        // Insert a key with unbounded interval and verify it's there
        fail_unless(STORE_PUT(&store, key10, 10, unbounded_interval(5, 10),
                              data10, 10, 1, &invalTag),
                    NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, unbounded_interval(5, 10), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(9, 10));
        check_entry(e, unbounded_interval(5, 10), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(10, PIN_INF));
        check_no_entry(e, interval(10, PIN_INF));

        // Increment the 'now' timestamp and verify item is extended
        Store_Invalidate(&store, 11, 0, NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(9, 10));
        check_entry(e, unbounded_interval(5, 10), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(10, PIN_INF));
        check_entry(e, unbounded_interval(5, 10), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(11, PIN_INF));
        check_no_entry(e, interval(11, PIN_INF));
        
        Store_Release(&store);
}
END_TEST;

START_TEST(lookup_implicit_invalidation)
{
        entry_t *e;
        const char *invalTag = "tag:";
        
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);

        // Set the initial timestamp in the store
        Store_Invalidate(&store, 15, 0, NULL);

        // Insert a key with unbounded interval
        fail_unless(STORE_PUT(&store, key10, 10, unbounded_interval(5, 10),
                              data10, 10, 1, &invalTag),
                    NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, unbounded_interval(5, 10), data10, 10);

        // Insert the same key, with interval starting > now
        fail_unless(STORE_PUT(&store, key10, 10, unbounded_interval(20, 25),
                              data10, 10, 1, &invalTag),
                    NULL);

        // Now we expect the first item to be bounded at now: [5,15)
        // and the second to be present as [20, 25+)
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, interval(5, 15), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(15, 20));
        check_no_entry(e, interval(15, 25));
        e = STORE_LOOKUP(&store, key10, 10, interval(20, PIN_INF));
        check_entry(e, unbounded_interval(20, 25), data10, 10);
        
        Store_Release(&store);
}
END_TEST;

#define IVN(n) interval(PIN_MIN + (n), PIN_MIN + (n) + 1)

START_TEST(eviction_basic)
{
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        for (int i = 0; i < 2 * N_BIG; ++i)
                fail_unless(STORE_PUT(&store, key10, 10, IVN(i),
                                      bigdata, BIG_LEN, 0, NULL), NULL);

        // Check the old items were evicted
        for (int i = 0; i < N_BIG; ++i)
                fail_if(STORE_LOOKUP(&store, key10, 10, IVN(i)), NULL);

        // Check that new items were not (with some margin for
        // overhead)
        for (int i = N_BIG + 256; i < 2 * N_BIG; ++i)
                fail_if(STORE_LOOKUP(&store, key10, 10, IVN(i)) == NULL, NULL);

        // XXX Test that lookup pushes something to the front

        Store_Release(&store);
}
END_TEST;

START_TEST(eviction_accounting)
{
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);

        // First, figure out how many items we can actually store
        for (int i = 0; i < N_BIG; ++i)
                fail_unless(STORE_PUT(&store, key10, 10, IVN(i),
                                      bigdata, BIG_LEN, 0, NULL), NULL);

        int storeCount = -1;
        for (int i = 0; i < N_BIG; ++i) {
                if (STORE_LOOKUP(&store, key10, 10, IVN(i))) {
                        storeCount = N_BIG - i;
                        break;
                }
        }
        Debug("Can store %d big items", storeCount);
        fail_if(storeCount == -1, NULL);
        fail_if(storeCount < N_BIG - 256, NULL);

        // Now push it really hard
        const int lots = 100000;
        for (int i = N_BIG; i < N_BIG + lots; ++i)
                fail_unless(STORE_PUT(&store, key10, 10, IVN(i),
                                      bigdata, BIG_LEN, 0, NULL), NULL);

        // Check that it's still storing the items we expect
        for (int i = N_BIG + lots/2; i < N_BIG + lots; ++i) {
                int distance = storeCount - ((N_BIG + lots) - i);
                interval_t iv = IVN(i);
                if (distance >= 0)
                        fail_if(STORE_LOOKUP(&store, key10, 10, iv) == NULL,
                                NULL);
                else
                        fail_if(STORE_LOOKUP(&store, key10, 10, iv), NULL);
        }

        Store_Release(&store);
}
END_TEST;

START_TEST(eviction_lru)
{
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);

        // Fill the store
        for (int i = 0; i < N_BIG; ++i)
                fail_unless(STORE_PUT(&store, key10, 10, IVN(i),
                                      bigdata, BIG_LEN, 0, NULL), NULL);

        // Touch something in the middle
        fail_if(STORE_LOOKUP(&store, key10, 10, IVN(N_BIG/4)) == NULL, NULL);

        // Put a bunch more in
        for (int i = N_BIG; i < N_BIG + N_BIG/2; ++i)
                fail_unless(STORE_PUT(&store, key10, 10, IVN(i),
                                      bigdata, BIG_LEN, 0, NULL), NULL);

        // Check that the touched one is still there
        fail_if(STORE_LOOKUP(&store, key10, 10, IVN(N_BIG/4)) == NULL, NULL);

        // Check that surrounding ones were evicted
        fail_if(STORE_LOOKUP(&store, key10, 10, IVN(N_BIG/4+1)), NULL);
        fail_if(STORE_LOOKUP(&store, key10, 10, IVN(N_BIG/4-1)), NULL);

        Store_Release(&store);
}
END_TEST;

START_TEST(eviction_iter_lru)
{
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);

        // Put a few items in the store
        for (int i = 0; i < 32; ++i)
                fail_unless(STORE_PUT(&store, key10, 10, IVN(i),
                                      bigdata, BIG_LEN, 0, NULL), NULL);

        // Touch something in the middle
        fail_if(STORE_LOOKUP(&store, key10, 10, IVN(8)) == NULL, NULL);

        entry_t *e;

        Store_EvictionOrderIterReset(&store);
        
        for (int i = 0; i < 32; i++)
        {
                // This is the one we touched, it should be last
                if (i == 8)
                        continue;

                // ...but everything else should be in order
                e = Store_EvictionOrderIterNext(&store);
                fail_unless(e->interval.lower == PIN_MIN+i);
        }

        // This should be the one we touched
        e = Store_EvictionOrderIterNext(&store);
        fail_unless(e->interval.lower == PIN_MIN+8);

        // And that should be the end of the list
        e = Store_EvictionOrderIterNext(&store);
        fail_unless(e == NULL);
        
        Store_Release(&store);
}
END_TEST;

START_TEST(invalidation)
{
        entry_t *e;
        const char *tag1 = "tag1:";
        const char *tag2 = "tag2:";
        const char *tag3 = "tag3:";
        const char *tags23[] = { tag2, tag3 };
        const char *tags13[] = { tag1, tag3 };
        
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);

        // Set the initial timestamp in the store
        Store_Invalidate(&store, 8, 0, NULL);
        
        // Insert two keys with different invalidation tag sets
        fail_unless(STORE_PUT(&store, key10, 10, unbounded_interval(5, 10),
                              data10, 10, 1, &tag1),
                    NULL);
        fail_unless(STORE_PUT(&store, otherkey10, 10,
                              unbounded_interval(7, 10),
                              otherdata10, 10, 2, tags23),
                    NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 9));
        check_entry(e, unbounded_interval(5, 10), data10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 9));
        check_entry(e, unbounded_interval(7, 10), otherdata10, 10);

        // Empty invalidation. Both keys should still be unbounded
        Store_Invalidate(&store, 11, 0, NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(10, 11));
        check_entry(e, unbounded_interval(5, 10), data10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(10, 11));
        check_entry(e, unbounded_interval(7, 10), otherdata10, 10);

        // Invalidate first item. First key should be bounded and
        // second unbounded.
        Store_Invalidate(&store, 12, 1, &tag1);
        e = STORE_LOOKUP(&store, key10, 10, interval(11, 12));
        check_entry(e, interval(5, 12), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(12, 13));
        check_no_entry(e, interval(12, 13));
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(11, 12));
        check_entry(e, unbounded_interval(7, 10), otherdata10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(12, 13));
        check_no_entry(e, interval(12, 13));

        // Empty invalidation. Second key should be extended, but not
        // first.
        Store_Invalidate(&store, 13, 0, NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(11, 12));
        check_entry(e, interval(5, 12), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(12, 13));
        check_no_entry(e, interval(11, 12));
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(11, 12));
        check_entry(e, unbounded_interval(7, 10), otherdata10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(12, 13));
        check_entry(e, unbounded_interval(7, 10), otherdata10, 10);
        
        
        // Invalidate second item. Both should be bounded.
        Store_Invalidate(&store, 14, 2, tags13);
        e = STORE_LOOKUP(&store, key10, 10, interval(11, 12));
        check_entry(e, interval(5, 12), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(13, 14));
        check_no_entry(e, interval(13, 14));
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(13, 14));
        check_entry(e, interval(7, 14), otherdata10, 10);

        // Empty invalidation. Nothing should change.
        Store_Invalidate(&store, 15, 0, NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(11, 12));
        check_entry(e, interval(5, 12), data10, 10);
        e = STORE_LOOKUP(&store, key10, 10, interval(13, 14));
        check_no_entry(e, interval(13, 14));
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(13, 14));
        check_entry(e, interval(7, 14), otherdata10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(14, 15));
        check_no_entry(e, interval(14, 15));
        
        Store_Release(&store);        
}
END_TEST;

START_TEST(invalidation_hierarchical)
{
        entry_t *e;
        const char *tagFoo = "foo:";
        const char *tagFooBar = "foo:bar:";
        const char *tagFooBaz = "foo:baz:";
        const char *tagFooBarBaz = "foo:bar:baz:";
        const char *tagBar = "bar:";

        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        store.collectStats = false;

        // Set the initial timestamp in the store
        Store_Invalidate(&store, 8, 0, NULL);

        // Insert some items
        fail_unless(STORE_PUT(&store, tagFoo, strlen(tagFoo),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFoo));
        fail_unless(STORE_PUT(&store, tagFooBar, strlen(tagFooBar),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFooBar));
        fail_unless(STORE_PUT(&store, tagFooBaz, strlen(tagFooBaz),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFooBaz));
        fail_unless(STORE_PUT(&store, tagFooBarBaz, strlen(tagFooBarBaz),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFooBarBaz));
        fail_unless(STORE_PUT(&store, tagBar, strlen(tagBar),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagBar));

        // Verify all unbounded
        e = STORE_LOOKUP(&store, tagFoo, strlen(tagFoo), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBar, strlen(tagFooBar), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBaz, strlen(tagFooBaz), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBarBaz, strlen(tagFooBarBaz), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
        e = STORE_LOOKUP(&store, tagBar, strlen(tagBar), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);

        // Invalidate foo:bar:
        Store_Invalidate(&store, 9, 1, &tagFooBar);
        e = STORE_LOOKUP(&store, tagFoo, strlen(tagFoo), interval(7, 8));
        check_entry(e, interval(7, 9), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBar, strlen(tagFooBar), interval(7, 8));
        check_entry(e, interval(7, 9), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBaz, strlen(tagFooBaz), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBarBaz, strlen(tagFooBarBaz), interval(7, 8));
        check_entry(e, interval(7, 9), data10, 10);
        e = STORE_LOOKUP(&store, tagBar, strlen(tagBar), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
}
END_TEST;

START_TEST(invalidation_out_of_order)
{
        entry_t *e;
        const char *tagFoo = "foo:";
        const char *tagFooBar = "foo:bar:";
        const char *tagFooBaz = "foo:baz:";
        const char *tagFooBarBaz = "foo:bar:baz:";
        const char *tagBar = "bar:";
        
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        store.collectStats = false;

        // Set the initial timestamp in the store
        Store_Invalidate(&store, 8, 0, NULL);

        // Insert one items
        fail_unless(STORE_PUT(&store, tagFooBar, strlen(tagFooBar),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFooBar));

        // Verify unbounded
        e = STORE_LOOKUP(&store, tagFooBar, strlen(tagFooBar), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);

        // Invalidate foo:bar:, verify bounded
        Store_Invalidate(&store, 9, 1, &tagFooBar);
        e = STORE_LOOKUP(&store, tagFooBar, strlen(tagFooBar), interval(7, 8));
        check_entry(e, interval(7, 9), data10, 10);

        // Insert foo: and foo:bar:baz: w/ earlier concrete upper
        // bound; they should be invalidated. foo:baz: should not.
        fail_unless(STORE_PUT(&store, tagFoo, strlen(tagFoo),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFoo));
        fail_unless(STORE_PUT(&store, tagFooBaz, strlen(tagFooBaz),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFooBaz));
        fail_unless(STORE_PUT(&store, tagFooBarBaz, strlen(tagFooBarBaz),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagFooBarBaz));
        e = STORE_LOOKUP(&store, tagFoo, strlen(tagFoo), interval(7, 8));
        check_entry(e, interval(7, 9), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBaz, strlen(tagFooBaz), interval(7, 8));
        check_entry(e, unbounded_interval(7, 8), data10, 10);
        e = STORE_LOOKUP(&store, tagFooBarBaz, strlen(tagFooBarBaz), interval(7, 8));
        check_entry(e, interval(7, 9), data10, 10);

        // Let the buffer expire (and send a null invalidation to
        // force expiry)
        fprintf(stderr, "Waiting 30 seconds to force buffer expiry.\n");
        sleep(30);
        Store_Invalidate(&store, 10, 0, NULL);
        
        // Try inserting bar: with the earlier timestamp. Because we
        // don't know if it's still valid, it should be truncated at
        // the concrete upper bound.
        fail_unless(STORE_PUT(&store, tagBar, strlen(tagBar),
                              unbounded_interval(7, 8),
                              data10, 10, 1, &tagBar));
        e = STORE_LOOKUP(&store, tagBar, strlen(tagBar), interval(7, 8));
        check_entry(e, interval(7, 8), data10, 10);
}
END_TEST;

START_TEST(dump_restore)
{
        entry_t *e;
        const char *invalTag = "tag:";
        int fd;
        const char *pathTemplate = "/tmp/store-test.XXXXXX";
        char path[1024];
        
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        
        // Insert a bounded key and an unbounded one.
        fail_unless(STORE_PUT(&store, key10, 10, interval(3, 8),
                              data10, 10, 1, &invalTag),
                    NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, interval(3, 8), data10, 10);

        fail_unless(STORE_PUT(&store, otherkey10, 10, unbounded_interval(5, 10),
                              otherdata10, 10, 1, &invalTag),
                    NULL);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 7));
        check_entry(e, unbounded_interval(5, 10), otherdata10, 10);

        // Create a temporary file and dump to it
        strcpy(path, pathTemplate);
        fd = mkstemp(path);
        fail_unless(fd != 0);
        Store_Dump(&store, fd);
        Store_Release(&store);
        close(fd);

        // Make new store and restore
        fail_unless(Store_Init(&store, DEFAULT_SIZE, DEFAULT_USELESS_BOUND), NULL);
        fd = open(path, O_RDONLY);
        fail_unless(fd != 0);
        Store_Load(&store, fd);
        close(fd);

        // Bounded key should not be restored
        // Unbounded key should exist at [-inf, MIN+)
        e = STORE_LOOKUP(&store, key10, 10, interval(PIN_NEG_INF,
                                                     PIN_INF));
        check_no_entry(e, interval(PIN_NEG_INF, PIN_INF));
        
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(PIN_NEG_INF,
                                                          PIN_INF));
        check_entry(e, unbounded_interval(PIN_NEG_INF, PIN_MIN),
                    otherdata10, 10);
        fail_unless(e->nInvalTags == 1);
        fail_unless(strcmp(e->invalTags[0], invalTag) == 0);

        fail_unless(unlink(path) == 0);
}
END_TEST;

START_TEST(discard_useless)
{
        entry_t *e;
        const char *invalTag = "tag:";

        fail_unless(Store_Init(&store, DEFAULT_SIZE, 5), NULL);

        // Insert a bounded key and an unbounded one.
        fail_unless(STORE_PUT(&store, key10, 10, interval(3, 8),
                              data10, 10, 1, &invalTag),
                    NULL);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, interval(3, 8), data10, 10);

        fail_unless(STORE_PUT(&store, otherkey10, 10, unbounded_interval(5, 10),
                              otherdata10, 10, 1, &invalTag),
                    NULL);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 7));
        check_entry(e, unbounded_interval(5, 10), otherdata10, 10);

        // Nothing should change.
        Store_RemoveUseless(&store);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, interval(3, 8), data10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 7));
        check_entry(e, unbounded_interval(5, 10), otherdata10, 10);

        // Wait two seconds and then invalidate the unbounded entry.
        // Both should still be present after RemoveUseless.
        sleep(2);
        Store_Invalidate(&store, 12, 1, &invalTag);
        Store_RemoveUseless(&store);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_entry(e, interval(3, 8), data10, 10);
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 7));
        check_entry(e, interval(5, 12), otherdata10, 10);

        // Wait five seconds. RemoveUseless should remove the first
        // entry.
        sleep(5);
        Store_RemoveUseless(&store);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_no_entry(e, interval(5, 7));
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 7));
        check_entry(e, interval(5, 12), otherdata10, 10);

        // Wait three seconds. RemoveUseless should remove the second
        // entry.
        sleep(5);
        Store_RemoveUseless(&store);
        e = STORE_LOOKUP(&store, key10, 10, interval(5, 7));
        check_no_entry(e, interval(5, 7));
        e = STORE_LOOKUP(&store, otherkey10, 10, interval(5, 7));
        check_no_entry(e, interval(5, 7));        
}
END_TEST;

int
main(void)
{
        Suite *s = suite_create("store");

        TCase *tc = tcase_create("Core");
        tcase_add_test(tc, init);
        tcase_add_test(tc, lookup_basic);
        tcase_add_test(tc, lookup_unbounded);
        tcase_add_test(tc, lookup_implicit_invalidation);
        tcase_add_test(tc, invalidation);
        tcase_add_test(tc, invalidation_hierarchical);
        tcase_add_test(tc, invalidation_out_of_order);
        tcase_add_test(tc, eviction_basic);
        tcase_add_test(tc, eviction_accounting);
        tcase_add_test(tc, eviction_lru);
        tcase_add_test(tc, eviction_iter_lru);
        tcase_add_test(tc, dump_restore);
        tcase_add_test(tc, discard_useless);
        tcase_set_timeout(tc, 60);
        suite_add_tcase(s, tc);

        return Test_Main(s);        
}
