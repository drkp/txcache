// -*- c-file-style: "bsd" -*-

#define _GNU_SOURCE  // For asprintf

#include "test-postgres.h"

#include <check.h>

#include <errno.h>
#include <glob.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <assert.h>

#include "lib/message.h"

static int postgresPid;
static int monitorFD;

static void
TestCheckWaitStatus(const char *cmd, int s)
{
        if (s == -1)
                fail("Wait on command '%s' failed: %s", cmd, strerror(errno));

        if (WIFEXITED(s) && WEXITSTATUS(s) == 0)
                return;

        if (WIFEXITED(s))
                fail("Command '%s' exited with status %d",
                     cmd, WEXITSTATUS(s));
        else if (WIFSIGNALED(s))
                fail("Command '%s' exited with signal %d",
                     cmd, WTERMSIG(s));
        else
                fail("Command '%s' failed for unknown reason", cmd);
}

static void
TestMonitorPostgres(int pgpid, int pipefd)
{
        PGconn *conn = PQconnectdb("dbname=postgres connect_timeout=1");
        if (conn == NULL || PQstatus(conn) != CONNECTION_OK) {
                PQfinish(conn);
                Panic("Monitor failed to connect to Postgres");
        }

        while (1) {
                fd_set rset;
                FD_ZERO(&rset);

                int ps = PQsocket(conn);
                if (ps <= 0)
                        Panic("Got invalid Postgres socket %d", ps);
                FD_SET(ps, &rset);
                FD_SET(pipefd, &rset);

                if (select(FD_SETSIZE, &rset, NULL, NULL, NULL) < 0)
                        PPanic("Monitor failed to select");
                if (FD_ISSET(pipefd, &rset)) {
                        // Parent process died, shutdown Postgres
                        PQfinish(conn);
                        kill(pgpid, SIGINT);
                        return;
                }
                if (FD_ISSET(ps, &rset)) {
                        PQconsumeInput(conn);
                        if (PQstatus(conn) != CONNECTION_OK) {
                                // Postgres died
                                PQfinish(conn);
                                return;
                        }
                }
        }
}

static void
TestCreatePGMonitor(int pgpid)
{
        int pipefd[2];
        if (pipe(pipefd) < 0)
                fail("Failed to create monitor pipe: %s", strerror(errno));

        int monitorpid;
        monitorpid = fork();
        if (monitorpid == -1)
                fail("Failed to fork: %s", strerror(errno));
        else if (monitorpid == 0) {
                close(pipefd[1]);
                TestMonitorPostgres(pgpid, pipefd[0]);
                exit(0);
        }

        close(pipefd[0]);
        monitorFD = pipefd[1];
}

void
Test_PGStart(void)
{
        char *cmd;
        int res;
        int ret;

        if (postgresPid)
                Panic("Test has already started a Postgres");

        const char *path = "../pg/bin";

        char cwd[1024];
        if (!getcwd(cwd, sizeof cwd))
                PPanic("Failed to get working directory");
        char *dbPath;
        ret = asprintf(&dbPath, "%s/.testdb", cwd);
        assert(ret != -1);
        setenv("PGDATA", dbPath, 1);
        setenv("PGHOST", dbPath, 1);

        // Initialize the test database
        if (access(dbPath, R_OK) == -1) {
                ret = asprintf(&cmd, "%s/initdb", path);
                assert(ret != -1);
                TestCheckWaitStatus(cmd, system(cmd));
                free(cmd);
        }

        // Start Postgres
        int pgpid;
        pgpid = fork();
        if (pgpid == -1)
                PPanic("Failed to fork");
        else if (pgpid == 0) {
                ret = asprintf(&cmd, "%s/postgres", path);
                assert(ret != -1);
                execl(cmd, cmd, "-k", dbPath, "-h", "",
                      "-c", "enable_bitmapscan=off",
                      "-c", "enable_tidscan=off",
                      "-c", "default_transaction_isolation=serializable",
                      "-c", "log_min_messages=fatal",
                      NULL);
                PPanic("Failed to exec postgres");
        }
        free(dbPath);

        // Spin until Postgres dies or comes up
        while (1) {
                int pgstatus;
                res = waitpid(pgpid, &pgstatus, WNOHANG);
                if (res == -1)
                        PPanic("waitpid failed");
                if (res != 0) {
                        TestCheckWaitStatus("postgres", pgstatus);
                        Panic("Postgres terminated early");
                }

                PGconn *conn = PQconnectdb("dbname=postgres connect_timeout=1");
                if (conn != NULL && PQstatus(conn) == CONNECTION_OK) {
                        PQfinish(conn);
                        break;
                }
                PQfinish(conn);

                // Wait 0.01 seconds
                usleep(10000);
        }

        // Create monitor process
        TestCreatePGMonitor(pgpid);

        // Create test database
        PGconn *conn = PQconnectdb("dbname=postgres connect_timeout=1");
        if (!conn)
                Panic("Failed to connect to Postgres");

        setenv("PGDATABASE", "testdb", 1);
        PGresult *pgres;
        pgres = PQexec(conn, "DROP DATABASE IF EXISTS testdb");
        PQclear(pgres);
        pgres = PQexec(conn, "CREATE DATABASE testdb");
        if (PQresultStatus(pgres) != PGRES_COMMAND_OK)
                Panic("Failed to create test database: %s",
                      PQerrorMessage(conn));
        PQclear(pgres);

        PQfinish(conn);

        postgresPid = pgpid;
}

void
Test_PGStop(void)
{
        if (!postgresPid)
                Panic("No Postgres running");

        // Request a shutdown through the monitor
        close(monitorFD);
        // Wait for Postgres to die
        waitpid(postgresPid, NULL, 0);
        postgresPid = 0;
}

PGconn *
Test_PGConnect(void)
{
        PGconn *conn = PQconnectdb("");
        if (!conn || PQstatus(conn) != CONNECTION_OK)
                fail("Failed to connect to database: %s",
                     PQerrorMessage(conn));
        return conn;
}
