// -*- c-file-style: "bsd" -*-

#include "lib/rpc.h"
#include "server/proto.h"

static void
Usage(char *progname)
{
        Panic("Usage: %s [-c] [hostname[:port]]", progname);
}

int
main(int argc, char **argv)
{
        char *hostname = "127.0.0.1";
        char *port = "16001";
        bool clear = false;

        int opt;
        while ((opt = getopt(argc, argv, "c")) != -1) {
                switch (opt) {
                case 'c':
                        clear = true;
                        break;
                default:
                        Usage(argv[0]);
                        break;
                }
        }

        if (optind == argc - 1) {
                hostname = strdup(argv[optind]);
                char *colonPos = strchr(hostname, ':');
                if (colonPos) {
                        port = colonPos + 1;
                        *colonPos = '\0';
                }
        } else if (optind != argc) {
                Usage(argv[0]);
        }

        host_t h;
        if (!RPCC_Connect(hostname, port, &h))
                PPanic("Failed to connect to cache server at %s:%s",
                       hostname, port);

        IOBuf_t *args = RPC_Start(&h, SERVER_REQ_STATS);
        IOBuf_PutInt8(args, clear);
        if (RPC_Send(&h) < 0)
                PPanic("Failed to send stats request");

        IOBuf_t result;
        int r = RPCC_Recv(&h, &result);
        if (r != SERVER_REP_STATS)
                Panic("Received unknown response %d", r);
        const char *stats = IOBuf_GetString(&result);

        printf("%s", stats);

        RPC_Release(&h);
        return 0;
}
