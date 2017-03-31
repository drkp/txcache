// -*- c-file-style: "bsd" -*-

#include "server.h"

#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>

#include "proto.h"
#include "store.h"
#include "lib/rpc.h"

static Store_t store;

static void
HandleLookup(host_t *h, IOBuf_t *args)
{
        const void *key;
        size_t keyLen;
        interval_t interval;
        pin_t earliestPin;
        StoreLookupPolicyId_t lookupPolicy;

        key = IOBuf_GetBuf(args, &keyLen);
        interval.lower = IOBuf_GetInt32(args);
        interval.upper = IOBuf_GetInt32(args);
        interval.stillValid = false;
        earliestPin = IOBuf_GetInt32(args);
        lookupPolicy = IOBuf_GetInt32(args);
        IOBuf_GetEOB(args);

        entry_t *entry = Store_Lookup(&store, key, keyLen, interval,
                                      earliestPin, lookupPolicy);

        IOBuf_t *resp = RPC_Start(h, entry ? SERVER_REP_FOUND : SERVER_REP_EMPTY);
        if (!resp)
                Panic("Failed to start lookup response");
        if (entry) {
                IOBuf_PutBuf(resp, entry->data, entry->dataLen);
                IOBuf_PutInt32(resp, entry->interval.lower);
                if (entry->interval.stillValid &&
                    (entry->interval.upper < store.lastInvalTime))
                        IOBuf_PutInt32(resp, store.lastInvalTime);
                else
                        IOBuf_PutInt32(resp, entry->interval.upper);
                IOBuf_PutInt8(resp, entry->interval.stillValid ? 1 : 0);
                //IOBuf_PutInt8(resp, 0);
                IOBuf_PutInt32(resp, entry->nInvalTags);
                for (int i = 0; i < entry->nInvalTags; i++)
                    IOBuf_PutString(resp, entry->invalTags[i]);
        }
        if (RPC_Send(h) < 0) {
                PWarning("Failed to send lookup response");
                RPC_Release(h);
        }
}

static void
HandlePut(host_t *h, IOBuf_t *args)
{
        const void *key;
        size_t keyLen;
        interval_t interval;
        const void *data;
        size_t dataLen;
        int32_t nTags, i;
        bool force;

        key = IOBuf_GetBuf(args, &keyLen);
        interval.lower = IOBuf_GetInt32(args);
        interval.upper = IOBuf_GetInt32(args);
        interval.stillValid = (IOBuf_GetInt8(args) != 0);
        data = IOBuf_GetBuf(args, &dataLen);
        nTags = IOBuf_GetInt32(args);

        const char *tags[nTags];
        
        for (i = 0; i < nTags; i++)
                tags[i] = IOBuf_GetString(args);

        force = (IOBuf_GetInt8(args) != 0);
        IOBuf_GetEOB(args);

        if (!Store_Put(&store, key, keyLen, interval, data, dataLen,
                       nTags, tags, force))
                Warning("Failed to put for " FMT_RPC_HOST,
                        VA_RPC_HOST(h));
}

static void
HandleStats(host_t *h, IOBuf_t *args)
{
        bool clear;

        clear = IOBuf_GetInt8(args);
        IOBuf_GetEOB(args);

        char *stats = Store_DumpStats(&store);

        if (clear)
                Store_ClearStats(&store);

        IOBuf_t *resp = RPC_Start(h, SERVER_REP_STATS);
        if (!resp)
                Panic("Failed to start stats response");
        IOBuf_PutString(resp, stats);
        if (RPC_Send(h) < 0) {
                PWarning("Failed to send stats response");
                RPC_Release(h);
        }
}

static void
HandleInval(host_t *h, IOBuf_t *args)
{
        int32_t nTags, i;
        pin_t pin;

        pin = IOBuf_GetInt32(args);
        
        nTags = IOBuf_GetInt32(args);

        const char *tags[nTags];
        
        for (i = 0; i < nTags; i++)
                tags[i] = IOBuf_GetString(args);

        Debug("Received invalidation for time " FMT_PIN " with %d tags",
                VA_PIN(pin), nTags);
        for (i = 0; i < nTags; i++)
                Debug("  tag %d: %s", i, tags[i]);
        
        Store_Invalidate(&store, pin, nTags, tags);
}

static void
HandleSig(host_t *h, IOBuf_t *args)
{
        int sig = IOBuf_GetInt32(args);
        IOBuf_GetEOB(args);
        switch (sig) {
        case SIGUSR1:
                Store_PrintStats(&store);
                break;
        case SIGUSR2:
                Store_Flush(&store);
                break;
        case SIGINT:
                Warning("Received SIGINT");
                Store_Release(&store);
#if PPROF
                // Ugh.  If we call C's exit here, C++ static
                // destructors do not get called, so the profile
                // doesn't get flushed.
                //
                // Unfortunately, profiler.h is a C++ header file in
                // perftools 0.8, which is the latest version of
                // perftools included in Debian amd64 because more
                // recent versions have all sorts of issues on 64-bit.
                extern void _Z12ProfilerStopv();
                _Z12ProfilerStopv();
#endif
                signal(SIGINT, SIG_DFL);
                kill(getpid(), SIGINT);
                break;
        case SIGQUIT:; {
                const char *dumpPath = "/tmp/store.dump";
                Warning("Dumping store to %s", dumpPath);

                int fd = open(dumpPath, O_WRONLY|O_CREAT, 0644);
                if (fd < 0)
                        Panic("Failed to open dump file %s: %s", dumpPath, strerror(errno));
                Store_Dump(&store, fd);
                Warning("Interrupting self");
                kill(getpid(), SIGINT);
                }
                break;                

        case SIGPWR:; {
                const char *dumpPath = "/tmp/store.debugdump";
                Warning("Dumping store debug dump to %s", dumpPath);

                int fd = open(dumpPath, O_WRONLY|O_CREAT, 0644);
                if (fd < 0)
                        Panic("Failed to open dump file %s: %s", dumpPath, strerror(errno));
                Store_DebugDump(&store, fd);
                close(fd);
                Warning("Done with debug dump");       
                }
                break;
        }
}

static host_t sigClient;
static host_t sigServer;

static void
SendSigRPC(int sig)
{
        IOBuf_t *resp = RPC_Start(&sigClient, SERVER_REQ_SIG);
        if (!resp)
                Panic("Failed to start signal message");
        IOBuf_PutInt32(resp,sig);
        if (RPC_Send(&sigClient) < 0)
                PWarning("Failed to send message");
}

bool
Server_Setup(server_t *server, size_t maxBytes, time_t staleness)
{
        if (!Store_Init(&store, maxBytes, staleness))
                return false;
	RPCS_RegisterHandler(server, SERVER_REQ_LOOKUP, HandleLookup);
        RPCS_RegisterHandler(server, SERVER_REQ_PUT, HandlePut);
        RPCS_RegisterHandler(server, SERVER_REQ_STATS, HandleStats);
        RPCS_RegisterHandler(server, SERVER_REQ_INVAL, HandleInval);

        // Set up signal handling
        RPCS_RegisterHandler(server, SERVER_REQ_SIG, HandleSig);
        if (!RPC_Pair(server, &sigServer, &sigClient))
                Panic("Could not get a socket pair!");
        signal(SIGUSR1, SendSigRPC);
        signal(SIGUSR2, SendSigRPC);
        signal(SIGINT, SendSigRPC);
        signal(SIGQUIT, SendSigRPC);
        signal(SIGPWR, SendSigRPC);
        signal(SIGSEGV, PanicOnSignal);

        return true;
}

void
Server_Shutdown(void)
{
        Store_Release(&store);
}

void
Server_Load(server_t *server, const char *path)
{
        int fd = open(path, O_RDONLY);
        if (fd < 0)
                Panic("Failed to open %s: %s", path, strerror(errno));
        Store_Load(&store, fd);
        close(fd);
}
