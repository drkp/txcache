d := $(dir $(lastword $(MAKEFILE_LIST)))

TEST_SRCS += $(addprefix $(d), \
	store-test.c)
SRCS += $(d)store-stress.c

$(d)store-test: $(o)store-test.o $(LIB-test) $(OBJS-server)
$(d)store-stress: $(o)store-stress.o $(OBJS-server)

TEST_BINS += $(d)store-test $(d)store-stress
