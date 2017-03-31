// -*- c-file-style: "bsd" -*-

#include "latency.h"

#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "lib/message.h"
#include "lib/iobuf-getput.h"

static struct Latency_t *latencyHead;

static void
LatencyInit(Latency_t *l, const char *name)
{
        memset(l, 0, sizeof *l);
        l->name = name;

        for (int i = 0; i < LATENCY_DIST_POOL_SIZE; ++i) {
                Latency_Dist_t *d = &l->distPool[i];
                d->min = ~0ll;
        }
}

void
_Latency_Init(Latency_t *l, const char *name)
{
        LatencyInit(l, name);
        l->next = latencyHead;
        latencyHead = l;
}

static void
LatencyMaybeFlush(void)
{
        static struct timespec lastFlush;

        struct timespec now;
        if (clock_gettime(CLOCK_MONOTONIC, &now) < 0)
                PPanic("Failed to get CLOCK_MONOTONIC");

        if (now.tv_sec != lastFlush.tv_sec) {
                lastFlush = now;
                Latency_Flush();
        }
}

static inline Latency_Dist_t *
LatencyAddHist(Latency_t *l, char type, uint64_t val, uint32_t count)
{
        if (!l->dists[(int)type]) {
                if (l->distPoolNext == LATENCY_DIST_POOL_SIZE) {
                        Panic("Too many distributions; maybe increase "
                              "LATENCY_DIST_POOL_SIZE");
                }
                l->dists[(int)type] = &l->distPool[l->distPoolNext++];
                l->dists[(int)type]->type = type;
        }
        Latency_Dist_t *d = l->dists[(int)type];

        int bucket = 0;
        val >>= 1;
        while (val) {
                val >>= 1;
                ++bucket;
        }
        Assert(bucket < LATENCY_NUM_BUCKETS);
        d->buckets[bucket] += count;

        return d;
}

static void
LatencyAdd(Latency_t *l, char type, uint64_t val)
{
        Latency_Dist_t *d = LatencyAddHist(l, type, val, 1);

        if (val < d->min)
                d->min = val;
        if (val > d->max)
                d->max = val;
        d->total += val;
        ++d->count;
}

static void
LatencyMap(void (*f)(Latency_t *))
{
        Latency_t *l = latencyHead;

        for (; l; l = l->next)
                f(l);
}

void
Latency_StartRec(Latency_t *l, Latency_Frame_t *fr)
{
        fr->accum = 0;
        fr->parent = l->bottom;
        l->bottom = fr;

        Latency_Resume(l);
}

void
Latency_EndRecType(Latency_t *l, Latency_Frame_t *fr, char type)
{
        Latency_Pause(l);

        Assert(l->bottom == fr);
        l->bottom = fr->parent;

        LatencyAdd(l, type, fr->accum);

        LatencyMaybeFlush();
}

void
Latency_Pause(Latency_t *l)
{
        struct timespec end;
        if (clock_gettime(CLOCK_MONOTONIC, &end) < 0)
                PPanic("Failed to get CLOCK_MONOTONIC");

        Latency_Frame_t *fr = l->bottom;
        uint64_t delta;
        delta = end.tv_sec - fr->start.tv_sec;
        delta *= 1000000000ll;
        if (end.tv_nsec < fr->start.tv_nsec) {
                delta -= 1000000000ll;
                delta += (end.tv_nsec + 1000000000ll) - fr->start.tv_nsec;
        } else {
                delta += end.tv_nsec - fr->start.tv_nsec;
        }
        fr->accum += delta;
}

void
Latency_Resume(Latency_t *l)
{
        if (clock_gettime(CLOCK_MONOTONIC, &l->bottom->start) < 0)
                PPanic("Failed to get CLOCK_MONOTONIC");
}

void
Latency_Sum(Latency_t *dest, Latency_t *summand)
{
        for (int i = 0; i < summand->distPoolNext; ++i) {
                Latency_Dist_t *d = &summand->distPool[i];
                for (int b = 0; b < LATENCY_NUM_BUCKETS; ++b) {
                        if (d->buckets[b] == 0)
                                continue;
                        LatencyAddHist(dest, d->type, 1ll<<b, d->buckets[b]);
                }
        }

        for (int i = 0; i < LATENCY_MAX_DIST; ++i) {
                Latency_Dist_t *dd = dest->dists[i];
                Latency_Dist_t *ds = summand->dists[i];
                if (!ds)
                        continue;

                if (ds->min < dd->min)
                        dd->min = ds->min;
                if (ds->max > dd->max)
                        dd->max = ds->max;
                dd->total += ds->total;
                dd->count += ds->count;
        }
}

static char *
LatencyFmtNS(uint64_t ns, char *buf)
{
        static const char *units[] = {"ns", "us", "ms", "s"};
        int unit = 0;
        while (ns >= 10000 && unit < (sizeof units / sizeof units[0]) - 1) {
                ns /= 1000;
                ++unit;
        }
        sprintf(buf, "%"PRIu64" %s", ns, units[unit]);
        return buf;
}

void
Latency_Dump(Latency_t *l)
{
        if (l->distPoolNext == 0) {
                // No distributions yet
                return;
        }

        char buf[5][64];

        // Keep track of the index of the first used distribution, and
        // for each other used distribution, the index of the next
        // used distribution.  This way we only have to make one scan
        // over all the distributions and the rest of our scans
        // (especially when printing the histograms) are fast.
        int firstType = -1;
        int nextTypes[LATENCY_MAX_DIST];
        int *ppnext = &firstType;

        for (int type = 0; type < LATENCY_MAX_DIST; ++type) {
                Latency_Dist_t *d = l->dists[type];
                if (!d)
                        continue;
                *ppnext = type;
                ppnext = &nextTypes[type];

                // Find the median
                uint64_t accum = 0;
                int medianBucket;
                for (medianBucket = 0; medianBucket < LATENCY_NUM_BUCKETS;
                     ++medianBucket) {
                        accum += d->buckets[medianBucket];
                        if (accum >= d->count / 2)
                                break;
                }

                char extra[3] = {'/', type, 0};
                if (type == '=')
                        extra[0] = '\0';
                QNotice("LATENCY %s%s: %s %s/%s %s (%"PRIu64" samples, %s total)",
                        l->name, extra, LatencyFmtNS(d->min, buf[0]),
                        LatencyFmtNS(d->total / d->count, buf[1]),
                        LatencyFmtNS((uint64_t)1 << medianBucket, buf[2]),
                        LatencyFmtNS(d->max, buf[3]), d->count,
                        LatencyFmtNS(d->total, buf[4]));
        }
        *ppnext = -1;

        // Find the count of the largest bucket so we can scale the
        // histogram
        uint64_t largestCount = LATENCY_HISTOGRAM_WIDTH;
        for (int i = 0; i < LATENCY_NUM_BUCKETS; ++i) {
                uint64_t total = 0;
                for (int dist = 0; dist < l->distPoolNext; ++dist) {
                        Latency_Dist_t *d = &l->distPool[dist];
                        total += d->buckets[i];
                }
                if (total > largestCount)
                        largestCount = total;
        }

        // Display the histogram
        int lastPrinted = LATENCY_NUM_BUCKETS;
        for (int i = 0; i < LATENCY_NUM_BUCKETS; ++i) {
                char bar[LATENCY_HISTOGRAM_WIDTH + 1];
                int pos = 0;
                uint64_t total = 0;
                for (int type = firstType; type != -1; type = nextTypes[type]) {
                        Latency_Dist_t *d = l->dists[type];
                        if (!d)
                                continue;
                        total += d->buckets[i];
                        int goal = ((total * LATENCY_HISTOGRAM_WIDTH)
                                    / largestCount);
                        for (; pos < goal; ++pos)
                                bar[pos] = type;
                }
                if (pos > 0) {
                        bar[pos] = '\0';
                        if (lastPrinted < i - 3) {
                                QNotice("%10s |", "...");
                        } else {
                                for (++lastPrinted; lastPrinted < i;
                                     ++lastPrinted)
                                        QNotice("%10s |",
                                                LatencyFmtNS((uint64_t)1 << lastPrinted,
                                                             buf[0]));
                        }
                        QNotice("%10s | %s",
                                LatencyFmtNS((uint64_t)1 << i, buf[0]),
                                bar);
                        lastPrinted = i;
                }
        }
}

void
Latency_DumpAll(void)
{
        LatencyMap(Latency_Dump);
}

void
Latency_Flush(void)
{
        static IOBuf_t buf;
        static bool inited = false;

        if (!inited)
                IOBuf_Init(&buf);

        Latency_t *l = latencyHead;
        for (; l; l = l->next)
                Latency_Put(l, &buf);

        if (access("/tmp/stats/", R_OK) < 0) {
                mkdir("/tmp/stats", 0777);
                chmod("/tmp/stats", 0777);
        }

        char fname[128];
        snprintf(fname, sizeof fname, "/tmp/stats/%d-l", getpid());

        // XXX Use IOBuf_WriteFile
        FILE *sf = fopen(fname, "w");
        if (!sf)
                Panic("Failed to open stats file %s", fname);
        size_t count;
        const void *data = IOBuf_PeekRest(&buf, &count);
        fwrite(data, 1, count, sf);
        IOBuf_Skip(&buf, count);
        fclose(sf);

        IOBuf_Shift(&buf);
}

void
Latency_Put(Latency_t *l, IOBuf_t *buf)
{
        IOBuf_PutString(buf, l->name);
        IOBuf_PutInt32(buf, l->distPoolNext);
        for (int i = 0; i < l->distPoolNext; ++i) {
                Latency_Dist_t *d = &l->distPool[i];
                IOBuf_PutInt8(buf, d->type);
                IOBuf_PutInt64(buf, d->min);
                IOBuf_PutInt64(buf, d->max);
                IOBuf_PutInt64(buf, d->total);
                IOBuf_PutInt64(buf, d->count);
                for (int b = 0; b < LATENCY_NUM_BUCKETS; ++b)
                        IOBuf_PutInt32(buf, d->buckets[b]);
        }
}

bool
Latency_TryGet(Latency_t *l, IOBuf_t *buf)
{
        const char *name = IOBuf_TryGetString(buf);
        if (!name)
                return false;
        LatencyInit(l, strdup(name)); // XXX Memory leak
        l->distPoolNext = IOBuf_GetInt32(buf);
        for (int i = 0; i < l->distPoolNext; ++i) {
                Latency_Dist_t *d = &l->distPool[i];
                d->type = IOBuf_GetInt8(buf);
                l->dists[(int)d->type] = d;
                d->min = IOBuf_GetInt64(buf);
                d->max = IOBuf_GetInt64(buf);
                d->total = IOBuf_GetInt64(buf);
                d->count = IOBuf_GetInt64(buf);
                for (int b = 0; b < LATENCY_NUM_BUCKETS; ++b)
                        d->buckets[b] = IOBuf_GetInt32(buf);
        }
        return true;
}
