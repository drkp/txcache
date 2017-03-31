// -*- c-file-style: "bsd" -*-

#include "iobuf.h"

#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>

#include "iobuf-getput.h"
#include "message.h"

#define DEFAULT_SIZE 256

const IOBuf_t IOBUF_NULL = IOBUF_NULL_IZER;

bool
IOBuf_Init(IOBuf_t *buf)
{
        *buf = IOBUF_NULL;

        buf->buf = malloc(DEFAULT_SIZE);
        if (!buf->buf)
                return false;
        buf->pos = buf->buf;
        buf->limit = buf->buf;
        buf->capacity = buf->buf + DEFAULT_SIZE;
        buf->error = 0;
        buf->fileBacked = false;
        return true;
}

bool
IOBuf_InitView(IOBuf_t *buf, const void *data, size_t len)
{
        *buf = IOBUF_NULL;

        // We discard the const qualifier, but ensure functionally
        // that the returned buffer cannot be written to.
        buf->buf = (char*)data;
        buf->pos = (char*)data;
        buf->limit = (char*)data + len;
        buf->capacity = NULL;
        buf->error = 0;
        return true;
}

void
IOBuf_InitFile(IOBuf_t *buf, int fd)
{
        off_t size = lseek(fd, 0, SEEK_END);
        if (size < 0) 
                Panic("lseek failed: %s", strerror(errno));

        void *filebuf = mmap(NULL, size, PROT_READ,
                             MAP_FILE | MAP_SHARED,
                             fd, 0);
        if (filebuf == MAP_FAILED)
                Panic("mmap failed: %s", strerror(errno));                

        if (madvise(filebuf, size, MADV_SEQUENTIAL))
                Warning("madvise failed: %s", strerror(errno));
        
        IOBuf_InitView(buf, filebuf, size);
        buf->fileBacked = true;
}

void
IOBuf_Release(IOBuf_t *buf)
{
        if (buf->fileBacked) {
                if (munmap(buf->buf, buf->limit - buf->buf) != 0)
                        Panic("munmap failed: %s", strerror(errno));
        } else if (buf->buf && buf->capacity) {
                free(buf->buf);
        }
        *buf = IOBUF_NULL;
}

void *
IOBuf_Reserve(IOBuf_t *buf, size_t count, const char *caller)
{
        if (buf->capacity == NULL)
                Panic("Cannot write to a read-only IOBuf (%s)", caller);

        if (buf->error)
                return NULL;
        
        if (buf->limit + count > buf->capacity) {
                // Resize buffer
                size_t curSize = buf->capacity - buf->buf;
                size_t goal = buf->limit - buf->buf + count;
                size_t newSize = curSize * 2;
                while (newSize < goal)
                        newSize *= 2;

                char *newBuf = realloc(buf->buf, newSize);
                if (!newBuf) {
                        Warning("IOBuf failed to expand from %d to %d bytes (%s)",
                                (int)curSize, (int)newSize, caller);
                        buf->error = ENOMEM;
                        free(buf->buf);
                        buf->buf = NULL;
                        return NULL;
                }

                // Fix pointers
                buf->pos = (buf->pos - buf->buf) + newBuf;
                buf->limit = (buf->limit - buf->buf) + newBuf;
                buf->capacity = newBuf + newSize;
                buf->buf = newBuf;
        }

        return buf->limit;
}

void
IOBuf_MoveLimit(IOBuf_t *buf, size_t count)
{
        if (buf->capacity == NULL)
                Panic("Cannot move the limit of a read-only IOBuf");

        if (buf->error)
                return;

        if (buf->limit + count > buf->capacity)
                Panic("Attempted to move limit from %d to %d, but capacity is %d",
                      (int)(buf->limit - buf->buf), (int)(buf->limit - buf->buf + count),
                      (int)(buf->capacity - buf->buf));

        buf->limit += count;
}

void
IOBuf_Shift(IOBuf_t *buf)
{
        IOBuf_Reserve(buf, 0, __func__);

        if (buf->nestedOffset != NESTED_OFFSET_NONE)
                Panic("Shifting with nested buffers not implemented");

        memmove(buf->buf, buf->pos, buf->limit - buf->pos);
        buf->limit -= buf->pos - buf->buf;
        buf->pos = buf->buf;
}

void
IOBuf_PutBytes(IOBuf_t *buf, const void *bytes, size_t count)
{
        void *dest = IOBuf_Reserve(buf, count, __func__);
        if (!dest)
                return;
        memcpy(dest, bytes, count);
        IOBuf_MoveLimit(buf, count);
}

const void *
IOBuf_TryGetBytes(IOBuf_t *buf, size_t count)
{
        if (buf->error)
                Panic("Attempt to get bytes from IOBuf with error %s",
                      strerror(buf->error));

        if (buf->nestedBound != NESTED_OFFSET_NONE &&
            buf->pos - buf->buf + count > buf->nestedBound)
                Panic("Attempt to get bytes overlapping with an unfinished "
                      "nested IOBuf");

        if (buf->pos + count > buf->limit)
                return NULL;

        void *res = buf->pos;
        buf->pos += count;
        return res;
}

void
IOBuf_BeginNested(IOBuf_t *buf)
{
        uint32_t startOffset = IOBuf_GetLimit(buf);
        IOBuf_PutInt32(buf, buf->nestedOffset);
        buf->nestedOffset = startOffset;
}

void
IOBuf_EndNested(IOBuf_t *buf)
{
        if (!IOBuf_InNested(buf))
                Panic("Not in a sub-buffer");
        uint32_t pos = IOBuf_GetPos(buf);
        uint32_t startOffset = buf->nestedOffset;
        uint32_t endOffset = IOBuf_GetLimit(buf);

        // Get the pushed parent header offset
        IOBuf_SetPos(buf, startOffset);
        buf->nestedOffset = IOBuf_GetInt32(buf);

        // Overwrite the header with our length
        IOBuf_PokeInt32(buf, startOffset,
                        (endOffset - startOffset) - sizeof(uint32_t));

        // Go back to where we were
        IOBuf_SetPos(buf, pos);
}

bool
IOBuf_InNested(IOBuf_t *buf)
{
        return (buf->nestedOffset != NESTED_OFFSET_NONE);
}

bool
IOBuf_TryGetNested(IOBuf_t *buf, IOBuf_t *nestedOut)
{
        size_t count;
        const void *data = IOBuf_TryGetBuf(buf, &count);
        if (!data)
                return false;

        return IOBuf_InitView(nestedOut, data, count);
}

void
IOBuf_WriteFile(IOBuf_t *buf, int fd)
{
        size_t count;
        const void *data = IOBuf_PeekRest(buf, &count);
        IOBuf_Skip(buf, count);

        while (count) {
                ssize_t written = write(fd, data, count);
                if (written < 0)
                        Panic("write failed: %s", strerror(errno));
                count -= written;
                data += written;
        }

        IOBuf_Shift(buf);
}
