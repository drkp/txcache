// -*- c-file-style: "bsd" -*-

#include "pincushion.h"

#include "checkdb.h"
#include "proto.h"
#include "lib/rpc.h"
#include "lib/assert.h"
#include <stdlib.h>

/* default arguments */
#define DBHOST                 "localhost"
#define MAX_STALENESS          120

void
usage(const char *progname)
{
        fprintf(stderr, "USAGE: %s -d DBNAME -u DBUSER [OPTIONS]\n", progname);
        fprintf(stderr,
                "   -d DBNAME: database name\n");
        fprintf(stderr,
                "   -u DBUSER: database server login username\n");
        fprintf(stderr,
                "   -h HOST: database server hostname (default: %s)\n",
                DBHOST);
        fprintf(stderr,
                "   -p PORT: port number to listen on (default: %s)\n",
                PINCUSHION_PORT);
        fprintf(stderr,
                "   -s SECONDS: maximum staleness to permit (default: %d)\n",
                MAX_STALENESS);        
        exit(1);
}

int
main(int argc, char **argv)
{
	server_t server;
        char c;
        const char *dbname = NULL;
        const char *dbhost = DBHOST;
        const char *dbuser = NULL;
        const char *port = PINCUSHION_PORT;
        unsigned long maxStaleness = MAX_STALENESS;
        char *strtolPtr;
        char dbconnstring[1024];
        struct timeval tv;

        /* parse arguments */
        while ((c = getopt (argc, argv, "d:h:p:s:u:")) != -1)
        {
                switch (c)
                {
                case 'd':
                        dbname = optarg;
                        break;
                case 'h':
                        dbhost = optarg;
                        break;
                case 'p':
                        /* yes, port number is a string -- this
                         * allows it to support service names */
                        port = optarg;
                        break;
                case 's':
                        maxStaleness = strtoul(optarg, &strtolPtr, 10);
                        if ((*optarg == '\0') || (*strtolPtr != '\0'))
                        {
                                fprintf(stderr,
                                        "option -s requires a numeric arg\n");
                                usage(argv[0]);
                        }
                        break;
                case 'u':
                        dbuser = optarg;
                        break;
                case '?':
                        fprintf(stderr,
                                "unknown option, or argument required: -%c\n",
                                optopt);
                        usage(argv[0]);
                        break;
                default:
                        NOT_REACHABLE();
                }
        }

        if ((dbname == NULL) || (dbuser == NULL)) {
                usage(argv[0]);
        }

        /*
         * Build dbconnstring
         *
         * XXX We don't currently validate arguments here or deal
         * particularly well with overly-long arguments -- totally
         * ignoring any possible security implications.
         */
        snprintf(dbconnstring, sizeof(dbconnstring),
                 "dbname=%s user=%s host=%s", dbname, dbuser, dbhost);

        tv.tv_sec = maxStaleness;
        tv.tv_usec = 0;
        
	RPCS_Init(&server, port);
	Pincushion_Setup(&server, dbconnstring, tv);
	CheckDB_CheckSettings();
	Reactor_Loop();
}
