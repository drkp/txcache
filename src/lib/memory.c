// -*- c-file-style: "bsd" -*-

#include "memory.h"

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>

char *
Memory_FmtSize(char *buf, size_t n)
{
        char suffix = 0;
        if ((n & 0x3ff) == 0) {
                n >>= 10;
                suffix = 'K';
        }
        if ((n & 0x3ff) == 0) {
                n >>= 10;
                suffix = 'M';
        }
        if ((n & 0x3ff) == 0) {
                n >>= 10;
                suffix = 'G';
        }
        if (suffix) {
                sprintf(buf, "%llu%c", (unsigned long long)n, suffix);
        } else {
                sprintf(buf, "%llu", (unsigned long long)n);
        }
        return buf;
}

static unsigned long long
Memory_ReadSize1(const char *buf, const char **endPtr)
{
        unsigned long long res = strtoull(buf, (char **)endPtr, 0);
        switch (**endPtr) {
        case 'G':
        case 'g':
                res <<= 10;
        case 'M':
        case 'm':
                res <<= 10;
        case 'K':
        case 'k':
                res <<= 10;
                ++(*endPtr);
        }
        return res;
}

size_t
Memory_ReadSize(const char *buf, const char **endPtr)
{
        unsigned long long ret = 0;
        bool more;

        do {
                ret += Memory_ReadSize1(buf, &buf);
                if (*buf == '+' && *(buf+1)) {
                        more = true;
                        ++buf;
                } else {
                        more = false;
                }
        } while (more);

        if (endPtr)
                *endPtr = buf;
        return (size_t)ret;
}
