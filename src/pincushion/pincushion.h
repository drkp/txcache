// -*- c-file-style: "bsd" -*-

#ifndef _PINCUSHION_PINCUSHION_H_
#define _PINCUSHION_PINCUSHION_H_

#include "lib/rpc.h"

void Pincushion_Setup(server_t *, const char *pgconninfo,
                      struct timeval maxStaleness);
void HandleSigStat(int);
void HandleSigFlush(int);
#endif
