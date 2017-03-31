// -*- c-file-style: "bsd" -*-

#ifndef _LIB_DBCONN_H_
#define _LIB_DBCONN_H_
#include <libpq-fe.h>

//database connection 
PGconn *db_conn;

void DB_Init(const char *conninfo);
void DB_Exit(void);
#endif
