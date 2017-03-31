// -*- c-file-style: "bsd" -*-

#ifndef _LIB_ASSERT_H_
#define _LIB_ASSERT_H_

/*
 * Assertion macros.
 *
 * Currently these mostly just wrap the standard C assert but
 * eventually they should tie in better with the logging framework.
 */
#include <assert.h>
#include <stdlib.h>
#include <stdio.h>

#define ASSERT(x) assert(x)

#define NOT_REACHABLE() do {                                            \
    fprintf(stderr, "NOT_REACHABLE point reached: %s, line %d",         \
            __FILE__, __LINE__);                                        \
    abort();                                                            \
} while (0)


#endif  /* _LIB_ASSERT_H */
