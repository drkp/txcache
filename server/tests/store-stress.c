// -*- c-file-style: "bsd" -*-

#include "../store.h"

#define STORE_BYTES (100*1024*1024)
#define STORE_USELESS_BOUND 86400
#define NUM_OPS 1000000

#define NUM_KEYS 500
#define MAX_KEY_LEN 100
#define MIN_DATA_LEN 8
#define MAX_DATA_LEN 8192

#define OPS_PER_TIME 1000
#define INTERVAL_DEVIATION 50

Store_t store;

int
main(int argc, char **argv)
{
        int lastPct = -1;
        char key[MAX_KEY_LEN], data[MAX_DATA_LEN];
        memset(key, 0, MAX_KEY_LEN);
        memset(data, 0, MAX_DATA_LEN);

        if (!Store_Init(&store, STORE_BYTES, STORE_USELESS_BOUND))
                Panic("Failed to initialize store");

        printf("Running store stress test\n");
        for (int i = 0; i < NUM_OPS; ++i) {
                // Generate a random key
                int kid = rand() % NUM_KEYS;
                int keyLen = sprintf(key + sizeof(uint32_t), "key%d", kid);
                keyLen += sizeof(uint32_t);
                *((uint32_t*)key) = 4;

                // Generate a random interval around now
                int now = INTERVAL_DEVIATION + i / OPS_PER_TIME;
                interval_t iv;
                iv.lower = (rand() % INTERVAL_DEVIATION) + now - INTERVAL_DEVIATION / 2;
                iv.upper = iv.lower + 1;
                iv.stillValid = false;

                // Look up
                entry_t *e = Store_Lookup(&store, key, keyLen, iv,
                                          PIN_NEG_INF, false);
                if (e) {
                        // XXX Check the entry
                } else {
                        // Generate a random datum
                        int dataLen = (rand() % (MAX_DATA_LEN - MIN_DATA_LEN))
                                + MIN_DATA_LEN;

                        // Insert it
                        if (!Store_Put(&store, key, keyLen, iv,
                                       data, dataLen, 0, NULL, false))
                                Panic("Failed to put");
                }
                int pct = (long long)i * 100 / (NUM_OPS - 1);
                if (pct != lastPct) {
                        printf("\r%d%%", pct);
                        fflush(stdout);
                        lastPct = pct;
                }
        }
        printf(": Passed\n");

        Store_Release(&store);
        return 0;
}
