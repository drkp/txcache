// -*- c-file-style: "bsd" -*-

#ifndef _LIB_MEMORY_H_
#define _LIB_MEMORY_H_

#include <unistd.h>

// Experimentally determined malloc size (for smallish objects, at
// least).  This is (v+8) rounded up to the nearest multiple of 16
// (though anything less than 24 takes 32 bytes).  Obviously this
// doesn't account for fragmentation.
#define MALLOC_SIZE(size) ((size) <= 24 ? 32 : (((size) + 7) | 15) + 1)

#define MEMORY_FMTSIZE_BUF 22

char *Memory_FmtSize(char *buf, size_t n);
size_t Memory_ReadSize(const char *buf, const char **endPtr);

#endif // _LIB_MEMORY_H_
