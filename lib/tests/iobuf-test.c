// -*- c-file-style: "bsd" -*-

#include "lib/iobuf.h"
#include "lib/iobuf-getput.h"

#include "lib/test.h"

#include <stdlib.h>

#define IOBUF(var, arr)                                 \
        IOBuf_t buf = IOBUF_NULL_IZER;                  \
        buf.buf = buf.pos = (arr);                      \
        buf.limit = buf.capacity = (arr) + sizeof(arr); \
        buf.error = 0

void
check_offsets(IOBuf_t *buf, int pos, int limit)
{
        fail_unless(buf->pos - buf->buf == pos,
                    "Expected position %d, got %d",
                    pos, buf->pos - buf->buf);
        fail_unless(buf->limit - buf->buf == limit,
                    "Expected limit %d, got %d",
                    limit, buf->limit - buf->buf);
}

START_TEST(init)
{
        IOBuf_t buf;
        fail_unless(IOBuf_Init(&buf), NULL);
        IOBuf_Release(&buf);
        IOBuf_Release(&buf);
}
END_TEST;

START_TEST(get_int32)
{
        char data[] = {1, 0, 0, 0};
        IOBUF(buf, data);
        int32_t x = 0;

        fail_unless(IOBuf_GetInt32(&buf) == 1, NULL);
        check_offsets(&buf, 4, 4);
        IOBuf_SetPos(&buf, 0);
        fail_unless(IOBuf_TryGetInt32(&buf, &x), NULL);
        fail_unless(x == 1, NULL);
        fail_if(IOBuf_TryGetInt32(&buf, &x), NULL);
}
END_TEST;

START_TEST(put_int32)
{
        char data[] = {1, 0, 0, 0};
        IOBuf_t buf;

        fail_unless(IOBuf_Init(&buf), NULL);
        IOBuf_PutInt32(&buf, 1);
        check_offsets(&buf, 0, 4);
        fail_unless(memcmp(buf.buf, data, sizeof data) == 0, NULL);
        IOBuf_Release(&buf);
}
END_TEST;

void *
make_junk(size_t count)
{
        void *junk = malloc(count);
        fail_if(junk == NULL, "Failed to allocate junk buffer");
        for (int i = 0; i < count; ++i)
                ((char*)junk)[i] = i & 0x7F;
        return junk;
}

void
check_junk(IOBuf_t *buf, size_t count)
{
        for (int i = 0; i < count; ++i)
                fail_unless(buf->buf[i] == (i & 0x7F), NULL);
}

START_TEST(put_to_n_times_capacity)
{
        IOBuf_t buf;
        fail_unless(IOBuf_Init(&buf), NULL);
        int cap = buf.capacity - buf.buf;
        void *junk = make_junk(_i * cap);
        IOBuf_PutBytes(&buf, junk, _i * cap);
        if (_i == 1) {
                fail_unless(buf.capacity - buf.buf == cap,
                            "Buffer expanded");
        } else {
                fail_if(buf.capacity < buf.pos, NULL);
                check_offsets(&buf, 0, _i * cap);
        }
        check_junk(&buf, _i * cap);
        free(junk);
        IOBuf_Release(&buf);
}
END_TEST;

START_TEST(in_nested)
{
        IOBuf_t buf;
        fail_unless(IOBuf_Init(&buf), NULL);
        fail_if(IOBuf_InNested(&buf), NULL);
        IOBuf_BeginNested(&buf);
        fail_unless(IOBuf_InNested(&buf), NULL);
        IOBuf_BeginNested(&buf);
        fail_unless(IOBuf_InNested(&buf), NULL);
        IOBuf_EndNested(&buf);
        fail_unless(IOBuf_InNested(&buf), NULL);
        IOBuf_BeginNested(&buf);
        fail_unless(IOBuf_InNested(&buf), NULL);
        IOBuf_EndNested(&buf);
        fail_unless(IOBuf_InNested(&buf), NULL);
        IOBuf_EndNested(&buf);
        fail_if(IOBuf_InNested(&buf), NULL);
        IOBuf_Release(&buf);
}
END_TEST;

int
main(void)
{
        Suite *s = suite_create("iobuf");

        TCase *tc = tcase_create("Core");
        tcase_add_test(tc, init);
        tcase_add_test(tc, get_int32);
        tcase_add_test(tc, put_int32);
        tcase_add_loop_test(tc, put_to_n_times_capacity, 1, 5);
        tcase_add_test(tc, in_nested);
        suite_add_tcase(s, tc);

        return Test_Main(s);
}
