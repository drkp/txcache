// -*- c-file-style: "bsd" -*-

#include "dbconn.h"
#include "message.h"

void
DB_Init(const char *conninfo)
{
	db_conn = PQconnectdb(conninfo);
	if (PQstatus(db_conn) != CONNECTION_OK) {
		Panic("Failed to connect to Postgres: %s",
                      PQerrorMessage(db_conn));
	}
        Notice("Connected to database with %s", conninfo);
}
	
void
DB_Exit(void)
{
    PQfinish(db_conn);
}
