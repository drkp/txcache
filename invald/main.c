// -*- c-file-style: "bsd" -*-

#include "invald.h"

#include "lib/rpc.h"
#include "lib/assert.h"
#include <stdlib.h>
#include <stdio.h>

/* default arguments */
#define DBHOST                 "localhost"

void
usage(const char *progname)
{
        fprintf(stderr, "USAGE: %s -d DBNAME -u DBUSER -n NODESFILE [OPTIONS]\n", progname);
        fprintf(stderr,
                "   -d DBNAME: database name\n");
        fprintf(stderr,
                "   -u DBUSER: database server login username\n");
        fprintf(stderr,
                "   -h HOST: database server hostname (default: %s)\n",
                DBHOST);
        fprintf(stderr,
                "   -n NODESFILE: file containing list of cache nodes\n"
                "                 (first two lines ignored)\n");
        exit(1);
}

int
main(int argc, char **argv)
{
        char c;
        const char *dbname = NULL;
        const char *dbhost = DBHOST;
        const char *dbuser = NULL;
        const char *nodefile = NULL;
        char dbconnstring[1024];
        FILE *nodes;

        /* parse arguments */
        while ((c = getopt (argc, argv, "d:h:n:u:")) != -1)
        {
                switch (c)
                {
                case 'd':
                        dbname = optarg;
                        break;
                case 'h':
                        dbhost = optarg;
                        break;
                case 'u':
                        dbuser = optarg;
                        break;
                case 'n':
                        nodefile = optarg;
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

        if ((dbname == NULL) || (dbuser == NULL) ||
            (nodefile == NULL)) {
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

        nodes = fopen(nodefile, "r");
        if (nodes == NULL)
        {
                perror("Unable to open nodes file");
                usage(argv[0]);
        }
	Invald_Setup(dbconnstring, nodes);
	Reactor_Loop();
}
