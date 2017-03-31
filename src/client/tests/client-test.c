// -*- c-file-style: "bsd" -*-

#include "lib/test.h"
#include "lib/test-postgres.h"

#include <stdlib.h>

#include "pincushion/pincushion.h"
#include "server/server.h"
#include "client/client.h"

static PGconn *pgconn;
static Client_t client;

static PGconn *
connectcb(Client_t *client, void *opaque)
{
        return pgconn;
}

static void
StartServers(void)
{
        // Start a pin cushion
        static server_t pincushion;
        struct timeval tv = {120,0};
        RPCS_Init(&pincushion, NULL);
        Pincushion_Setup(&pincushion, "dbname=testdb", tv);

        ClientNode_t *pincushionNode = malloc(sizeof *pincushionNode);
        static host_t pincushionServer;
        if (!RPC_Pair(&pincushion, &pincushionServer, &pincushionNode->host))
                fail("Failed to create pincushion server/client pair");

        // Start a cache server
        static server_t server;
        RPCS_Init(&server, NULL);
        Server_Setup(&server, 8*1024*1024, 30);

        ClientNode_t *cacheNodes = malloc(sizeof *cacheNodes);
        static host_t cacheServer;
        if (!RPC_Pair(&server, &cacheServer, &cacheNodes[0].host))
                fail("Failed to create cache server/client pair");

        // Create a client
        pgconn = Test_PGConnect();

        fail_unless(Client_InitWith(&client, connectcb, NULL, NULL,
                                    pincushionNode, 1, cacheNodes),
                    "Failed to initialize client");
}

START_TEST(begin_commit)
{
        StartServers();
        fail_unless(Client_BeginRO(&client, 1.0),
                    "Failed to begin RO");
        fail_unless(Client_Commit(&client),
                    "Failed to commit");
}
END_TEST;

int
main(void)
{
        Test_PGStart();
        Reactor_EagerMode();

        Suite *s = suite_create("client");

        TCase *tc = tcase_create("Core");
        tcase_add_test(tc, begin_commit);
        suite_add_tcase(s, tc);

        int result = Test_Main(s);

        Test_PGStop();

        return result;
}
