// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_SERVER_H_
#define _SERVER_SERVER_H_

#include <stdbool.h>
#include <unistd.h>
#include "lib/rpc.h"

bool Server_Setup(server_t *server, size_t maxBytes, time_t staleness);
void Server_Shutdown(void);

void Server_Load(server_t *server, const char *path);

#endif // _SERVER_SERVER_H_
