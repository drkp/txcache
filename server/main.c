// -*- c-file-style: "bsd" -*-

#include "server.h"

#include <unistd.h>
#include <stdlib.h>

#include "proto.h"
#include "lib/rpc.h"
#include "lib/memory.h"

#define MAX_STALENESS 60         /* if not specified on command line */

static void
Usage(const char *progName)
{
        fprintf(stderr, "Usage: %s [-p port] [-l snapshot] [-t max-staleness] -s size\n",
                progName);
        exit(1);
}

int
main(int argc, char **argv)
{
	server_t server;
        size_t cacheSize = 0;
        const char *port = SERVER_PORT;
        const char *loadFile = NULL;
        time_t maxStaleness = MAX_STALENESS;

        int opt;
        while ((opt = getopt(argc, argv, "p:s:l:t:")) != -1) {
                switch (opt) {
                case 'p':
                        port = optarg;
                        break;
                case 's':
                {
                        const char *end;
                        cacheSize = Memory_ReadSize(optarg, &end);
                        if (*end) {
                                fprintf(stderr, "Illegal size %s\n", optarg);
                                Usage(argv[0]);
                        }
                        break;
                }
                case 't':
                {
                        char *strtolPtr;
                        maxStaleness = strtoul(optarg, &strtolPtr, 10);
                        if ((*optarg == '\0') || (*strtolPtr != '\0'))
                        {
                                fprintf(stderr,
                                        "option -t requires a numeric arg\n");
                                Usage(argv[0]);
                        }
                        break;
                }
                case 'l':
                        loadFile = optarg;
                        break;
                default:
                        fprintf(stderr, "Unknown argument %s\n", argv[optind]);
                        Usage(argv[0]);
                        break;
                }
        }
        if (cacheSize == 0) {
                fprintf(stderr, "No cache size given\n");
                Usage(argv[0]);
        }

        RPCS_Init(&server, port); 
        if (!Server_Setup(&server, cacheSize, maxStaleness))
                Panic("Failed to set up server");

        if (loadFile)
                Server_Load(&server, loadFile);

        char fmtbuf[MEMORY_FMTSIZE_BUF];
        Notice("Created %s cache", Memory_FmtSize(fmtbuf, cacheSize));
        Reactor_Loop();
        Server_Shutdown();
}
