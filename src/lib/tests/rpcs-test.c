#include "lib/rpc.h"

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include <sys/select.h>

void
test_handler(host_t *h, IOBuf_t *args) {
  const char *s = IOBuf_GetString(args);
  printf("Incoming rpc with message %s\n",s);
  RPC_Start(h, 'K');
  RPC_Send(h);
}

int
main(int argc, char **argv) {
  server_t serv;
  RPCS_Init(&serv, "12345");
  RPCS_RegisterHandler(&serv, 'T',&test_handler);
  Reactor_Loop();

  return 0;
}
