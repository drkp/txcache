#include "lib/rpc.h"

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>

int 
main(int argc, char **argv) {
  host_t h;
  char r;
  IOBuf_t result;

  if (!RPCC_Connect("127.0.0.1", "12345", &h))
    Panic("Failed to connect to server");
  
  IOBuf_t *args = RPC_Start(&h, 'T');
  IOBuf_PutString(args, "hello\n");
  RPC_Send(&h);
  r = RPCC_Recv(&h, &result);
  printf("received from server: %c\n",r);

  while(1) {};


  return 0;
}
