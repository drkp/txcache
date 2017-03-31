d := $(dir $(lastword $(MAKEFILE_LIST)))

SRCS += $(addprefix $(d), \
	sumlatencies.c serverstats.c)

$(d)sumlatencies: $(o)sumlatencies.o $(LIB-iobuf) $(LIB-latency)
LDFLAGS-$(d)sumlatencies += -lrt

$(d)serverstats: $(o)serverstats.o $(LIB-iobuf) $(LIB-rpcc)

BINS += $(d)sumlatencies $(d)serverstats
