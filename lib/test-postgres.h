#ifndef _LIB_TEST_POSTGRES_H_
#define _LIB_TEST_POSTGRES_H_

#include <libpq-fe.h>

void Test_PGStart(void);
void Test_PGStop(void);
PGconn *Test_PGConnect(void);

#endif // _LIB_TEST_POSTGRES_H_
