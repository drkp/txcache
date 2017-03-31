d := $(dir $(lastword $(MAKEFILE_LIST)))

SRCS += $(addprefix $(d), \
	main.c server.c store.c storevset.c marshal.c lookuppolicy.c \
	evictionpolicy.c)

OBJS-server := $(o)server.o $(o)store.o $(o)storevset.o $(o)marshal.o \
        $(o)lookuppolicy.o $(o)evictionpolicy.o \
	$(LIB-rpcs) $(LIB-hashtable) $(LIB-message)

$(d)server: $(o)main.o $(OBJS-server) $(LIB-memory)

BINS += $(d)server

SRCS += $(d)proxy.c
OBJS-serverproxy := $(o)proxy.o

include $(d)tests/Rules.mk
