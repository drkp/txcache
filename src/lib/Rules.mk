d := $(dir $(lastword $(MAKEFILE_LIST)))

SRCS += $(addprefix $(d), \
	lookup3.c message.c iobuf.c rpc.c rpcc.c rpcs.c memory.c dbconn.c \
	pinset.c reactor.c latency.c)
$(call add-CFLAGS,$(d)dbconn.c,$(PG_CFLAGS))

TEST_SRCS += $(d)test.c $(d)test-postgres.c
$(call add-CFLAGS,$(d)test-postgres.c,$(PG_CFLAGS))

LIB-hash := $(o)lookup3.o

LIB-message := $(o)message.o $(LIB-hash)

LIB-iobuf := $(o)iobuf.o $(LIB-message)

LIB-reactor := $(o)reactor.o $(LIB-message)

LIB-rpcc := $(o)rpcc.o $(o)rpc.o $(LIB-reactor) $(LIB-iobuf) $(LIB-message)

LIB-rpcs := $(o)rpcs.o $(o)rpc.o $(LIB-reactor) $(LIB-iobuf) $(LIB-message)

LIB-hashtable := $(LIB-hash) $(LIB-message)

LIB-memory := $(o)memory.o

LIB-dbconn := $(o)dbconn.o

LIB-pinset := $(o)pinset.o $(LIB-message)

LIB-test := $(o)test.o $(LIB-message)

LIB-test-postgres := $(o)test-postgres.o $(LIB-message)

LIB-latency := $(o)latency.o $(LIB-message)

include $(d)tests/Rules.mk
