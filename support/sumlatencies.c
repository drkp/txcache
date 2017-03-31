#include <stdio.h>

#include "lib/iobuf.h"
#include "lib/iobuf-getput.h"
#include "lib/latency.h"
#include "lib/tmpl/hashtable.h"

%instance Hashtable LHT(struct LHT_Entry, link);

typedef struct LHT_Entry
{
        Latency_t l;
        LHT_Link_t link;
} LHT_Entry;

%instance HashtableImpl LHT(struct LHT_Entry, link);

static void
ReadAll(FILE *fp, IOBuf_t *buf)
{
        char rbuf[1024];
        while (1) {
                int count = fread(rbuf, 1, sizeof rbuf, fp);
                if (count == 0)
                        break;
                IOBuf_PutBytes(buf, rbuf, count);
        }
}

static int
cmpstring(const void *p1, const void *p2)
{
        return strcmp(* (char * const *) p1, * (char * const *) p2);
}

int
main(int argc, char **argv)
{
        // Read files into an IOBuf
        IOBuf_t buf;
        IOBuf_Init(&buf);
        if (argc == 1)
                // XXX Use IOBuf_ReadFile
                ReadAll(stdin, &buf);
        else {
                for (int i = 1; i < argc; ++i) {
                        FILE *fp = fopen(argv[i], "rb");
                        if (!fp)
                                Panic("Failed to open %s", argv[i]);
                        ReadAll(fp, &buf);
                        fclose(fp);
                }
        }

        // Decode all latency structures and sum them
        LHT_t lht;
        LHT_Init(&lht);

        LHT_Entry *newEntry = malloc(sizeof *newEntry);
        while (Latency_TryGet(&newEntry->l, &buf)) {
                LHT_Entry *existing = LHT_Get(&lht, newEntry->l.name,
                                              strlen(newEntry->l.name) + 1);
                if (existing) {
                        Latency_Sum(&existing->l, &newEntry->l);
                } else {
                        LHT_Insert(&lht, newEntry, newEntry->l.name,
                                   strlen(newEntry->l.name) + 1);
                        newEntry = malloc(sizeof *newEntry);
                }
        }

        // Get the names of all latency structures
        int nLatencies = LHT_Size(&lht, NULL);
        const char **names = malloc(nLatencies * sizeof *names);
        LHT_Iter_t iter;
        LHT_Iter(&lht, &iter);
        LHT_Entry *entry;
        for (int i = 0; (entry = LHT_IterNext(&iter)); ++i) {
                names[i] = entry->l.name;
        }

        // Alphabetize the latency names
        qsort(names, nLatencies, sizeof *names, cmpstring);

        // Dump all latency structures
        for (int i = 0; i < nLatencies; ++i) {
                LHT_Entry *entry = LHT_Get(&lht, names[i], strlen(names[i]) + 1);
                Latency_Dump(&entry->l);
        }
}
