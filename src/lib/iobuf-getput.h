// -*- c-file-style: "bsd" -*-

#ifndef _LIB_IOBUF_GETPUT_H_
#define _LIB_IOBUF_GETPUT_H_

#include "iobuf.h"

#include <stdint.h>
#include <string.h>

#include "message.h"

#define TRY_GET(try)                                                    \
        ({                                                              \
                const void *res = IOBuf_TryGetBytes(buf, sizeof *x);    \
                if (res)                                                \
                        *x = *(__typeof__(x))res;                       \
                res != NULL;                                            \
        })

#define GET(typ, try)                                           \
        ({                                                      \
                typ x;                                          \
                if (!try(buf, &x))                              \
                        Panic("Failed to get a " # typ);        \
                x;                                              \
        })

static inline void
IOBuf_PutInt8(IOBuf_t *buf, int8_t x)
{
        IOBuf_PutBytes(buf, &x, sizeof x);
}

static inline bool
IOBuf_TryGetInt8(IOBuf_t *buf, int8_t *x)
{
        return TRY_GET();
}

static inline int8_t
IOBuf_GetInt8(IOBuf_t *buf)
{
        return GET(int8_t, IOBuf_TryGetInt8);
}

static inline void
IOBuf_PokeInt32(IOBuf_t *buf, size_t offset, int32_t x)
{
        Assert(buf->capacity);
        Assert(buf->buf + offset + sizeof x <= buf->limit);
        // XXX Byte order
        *(int32_t*)(buf->buf + offset) = x;
}

static inline void
IOBuf_PutInt32(IOBuf_t *buf, int32_t x)
{
        // XXX Byte order
        IOBuf_PutBytes(buf, &x, sizeof x);
}

static inline bool
IOBuf_TryGetInt32(IOBuf_t *buf, int32_t *x)
{
        return TRY_GET();
}

static inline int32_t
IOBuf_GetInt32(IOBuf_t *buf)
{
        return GET(int32_t, IOBuf_TryGetInt32);
}

static inline void
IOBuf_PutInt64(IOBuf_t *buf, int64_t x)
{
        // XXX Byte order
        IOBuf_PutBytes(buf, &x, sizeof x);
}

static inline bool
IOBuf_TryGetInt64(IOBuf_t *buf, int64_t *x)
{
        return TRY_GET();
}

static inline int64_t
IOBuf_GetInt64(IOBuf_t *buf)
{
        return GET(int64_t, IOBuf_TryGetInt64);
}

static inline void
IOBuf_PutDouble(IOBuf_t *buf, double x)
{
        IOBuf_PutBytes(buf, &x, sizeof x);
}

static inline bool
IOBuf_TryGetDouble(IOBuf_t *buf, double *x)
{
        return TRY_GET();
}

static inline double
IOBuf_GetDouble(IOBuf_t *buf)
{
        return GET(double, IOBuf_TryGetDouble);
}

#undef TRY_GET
#undef GET

static inline void
IOBuf_PutBuf(IOBuf_t *buf, const void *data, size_t count)
{
        IOBuf_PutInt32(buf, count);
        IOBuf_PutBytes(buf, data, count);
}

static inline const void *
IOBuf_TryGetBuf(IOBuf_t *buf, size_t *count)
{
        int pos = IOBuf_GetPos(buf);
        int32_t xcount;
        const void *res;
        if (!IOBuf_TryGetInt32(buf, &xcount) ||
            !(res = IOBuf_TryGetBytes(buf, xcount))) {
                IOBuf_SetPos(buf, pos);
                return NULL;
        }
        if (count)
                *count = (size_t)xcount;
        return res;
}

static inline const void *
IOBuf_GetBuf(IOBuf_t *buf, size_t *count)
{
        const void *res;
        if (!(res = IOBuf_TryGetBuf(buf, count)))
                Panic("Failed to get a buffer");
        return res;
}

static inline void
IOBuf_PutString(IOBuf_t *buf, const char *str)
{
        IOBuf_PutBuf(buf, str, strlen(str) + 1);
}

static inline const char *
IOBuf_TryGetString(IOBuf_t *buf)
{
        size_t count;
        const char *res = IOBuf_TryGetBuf(buf, &count);
        if (!res)
                return NULL;
        if (res[count-1] != '\0')
                Panic("Received non NULL-terminated string");
        return res;
}

static inline const char *
IOBuf_GetString(IOBuf_t *buf)
{
        const char *res = IOBuf_TryGetString(buf);
        if (!res)
                Panic("Failed to get a string");
        return res;
}

static inline void
IOBuf_GetEOB(IOBuf_t *buf)
{
        size_t remaining = IOBuf_GetLimit(buf) - IOBuf_GetPos(buf);
        if (remaining)
                Panic("Failed to get end of buffer (%u bytes remaining)",
                      (unsigned)remaining);
}

#endif // _LIB_IOBUF_GETPUT_H_
