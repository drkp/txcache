// -*- c-file-style: "bsd" -*-

/**
 * Dynamically sized FIFO-like buffers for serializing and
 * deserializing binary data.
 */

#ifndef _LIB_IOBUF_H_
#define _LIB_IOBUF_H_

#include <stdbool.h>
#include <stdint.h>
#include <unistd.h>
#include <errno.h>

#include "message.h"

#define NESTED_OFFSET_NONE ((uint32_t)~0)

typedef struct IOBuf_t
{
        // Pointer to the beginning of the buffer.
        char *buf;
        // Pointer to the current position in the buffer.
        char *pos;
        // Pointer to the next byte of the buffer that should be
        // written to.
        char *limit;
        // Pointer to one byte past the end of the buffer or NULL for
        // view buffers.  View buffers cannot be written to and will
        // not be freed.
        char *capacity;
        // Any error that occurred while writing to the buffer, or 0
        // if no errors have occurred.  Put operations should be
        // no-ops when this is non-zero.
        int error;
        // The offset into buf of the header of the inner-most nested
        // buffer or NESTED_OFFSET_NONE if none.  At this offset into
        // the buffer there will be a uint32_t storing the offset of
        // its enclosing nested buffer, etc.
        uint32_t nestedOffset;
        // The offset into buf of the outer-most nested buffer or
        // NESTED_OFFSET_NONE if we are not in a nested buffer.  Reads
        // must not go beyond this point.
        uint32_t nestedBound;
        // True if the buffer is actually a memory-mapped file
        // (created with IOBuf_InitFile)
        bool fileBacked;
} IOBuf_t;

#define IOBUF_NULL_IZER                                         \
        {                                                       \
                .buf = NULL,                                    \
                .error = EINVAL,                                \
                .fileBacked = false,                            \
                .nestedOffset = NESTED_OFFSET_NONE,             \
                .nestedBound = NESTED_OFFSET_NONE,              \
        }

extern const IOBuf_t IOBUF_NULL;

/**
 * Initialize an IOBuf to a small, empty buffer.
 */
bool IOBuf_Init(IOBuf_t *buf);
/**
 * Initialize a read-only view buffer backed by the given data.
 */
bool IOBuf_InitView(IOBuf_t *buf, const void *data, size_t len);
/**
 * Initialize a read-only view buffer backed by the contents of the
 * given file descriptor.
 */
void IOBuf_InitFile(IOBuf_t *buf, int fd);
/**
 * Release an IOBuf, freeing resources allocated to it.  This is
 * idempotent and it is always safe to release IOBUF_NULL.
 */
void IOBuf_Release(IOBuf_t *buf);

/**
 * Reserve some number of bytes in the buffer for writing to.
 * Typically, this should be followed by some modification of the data
 * at the returned pointer and a call to IOBuf_MoveLimit.  caller
 * specifies the name of the calling function for error reporting.  It
 * should usually be given as __func__.
 *
 * Note that any pointers into the buffer (such as those returned by
 * Get functions) are NO LONGER VALID after a reserve operation.
 */
void *IOBuf_Reserve(IOBuf_t *buf, size_t count, const char *caller);
/**
 * Move the limit of the given buffer.  Note that this will panic if
 * the buffer does not have enough capacity, so it should typically
 * only follow an IOBuf_Reserve.
 */
void IOBuf_MoveLimit(IOBuf_t *buf, size_t count);
/**
 * Shift a buffer so that the current position in the buffer is at
 * offset 0 and any data preceding the position is freed.
 *
 * Note that any positions returned by IOBuf_GetPos are NO LONGER
 * VALID after a shift operation.
 */
void IOBuf_Shift(IOBuf_t *buf);

/**
 * Copy bytes into a buffer starting at the buffer's current limit.
 * If the operation fails for any reason, sets the error indicator in
 * the buffer.
 *
 * Note that any pointers into the buffer (such as those returned by
 * Get functions) are NO LONGER VALID after this or ANY Put operation.
 */
void IOBuf_PutBytes(IOBuf_t *buf, const void *bytes, size_t count);
/**
 * Try getting some number of bytes from the buffer, starting at the
 * current position.  If there are fewer than count bytes in the
 * buffer, this returns NULL without affecting the buffer state.
 *
 * The returned pointer points directly into the buffer.  Thus, it
 * should not be freed or written to and will become invalid after any
 * Put operation.
 */
const void *IOBuf_TryGetBytes(IOBuf_t *buf, size_t count);

/**
 * Begin putting a nested IOBuf.
 */
void IOBuf_BeginNested(IOBuf_t *buf);
/**
 * Finish the inner-most nested IOBuf.
 */
void IOBuf_EndNested(IOBuf_t *buf);
/**
 * Return true if the buffer is currently in a nested IOBuf.
 */
bool IOBuf_InNested(IOBuf_t *buf);
/**
 * Get a nested IOBuf from the buffer.  The returned IOBuf will be
 * backed by the same memory as the given IOBuf.  Because of this, it
 * cannot be written to and need not be freed (though doing so is
 * harmless).
 */
bool IOBuf_TryGetNested(IOBuf_t *buf, IOBuf_t *nestedOut);
/**
 * Write the contents of buf from the cursor to the end to fd.
 * Consumes the contents of buf.
 */
void IOBuf_WriteFile(IOBuf_t *buf, int fd);

/**
 * Get the buffer's error state.  Returns 0 if there are no errors or
 * an errno if something went wrong.
 */
static inline int
IOBuf_Error(IOBuf_t *buf)
{
        return buf->error;
}

/**
 * Get the current read position in the buffer.  This is primarily
 * meant so TryGet operations composed of other TryGet operations can
 * back up to where they started if anything fails.
 */
static inline uint32_t
IOBuf_GetPos(IOBuf_t *buf)
{
        if (buf->error)
                return 0;
        return buf->pos - buf->buf;
}

/**
 * Get the current write position in the buffer.
 */
static inline uint32_t
IOBuf_GetLimit(IOBuf_t *buf)
{
        if (buf->error)
                return 0;
        return buf->limit - buf->buf;
}

/**
 * Set the read position in the buffer to something previously
 * retrieved with IOBuf_GetPos.
 */
static inline void
IOBuf_SetPos(IOBuf_t *buf, uint32_t pos)
{
        buf->pos = buf->buf + pos;
        if (buf->pos > buf->limit)
                Panic("Position %u is beyond limit %u",
                      pos, (uint32_t)(buf->limit - buf->buf));
}

/**
 * Move the read position forward.
 */
static inline void
IOBuf_Skip(IOBuf_t *buf, uint32_t count)
{
        IOBuf_SetPos(buf, IOBuf_GetPos(buf) + count);
}

/**
 * Return the remaining bytes in the buffer, without consuming them.
 */
static inline const void *
IOBuf_PeekRest(IOBuf_t *buf, size_t *count)
{
        *count = buf->limit - buf->pos;
        return IOBuf_TryGetBytes(buf, 0);
}

#endif // _LIB_IOBUF_H_
