d := $(dir $(lastword $(MAKEFILE_LIST)))

MY_SRCS := $(d)client-test.c
TEST_SRCS += $(MY_SRCS)
$(call add-CFLAGS,$(MY_SRCS),$(PG_CFLAGS))

$(d)client-test: $(o)client-test.o $(LIB-test) $(LIB-test-postgres) \
	$(LIB-rpcs) $(LIB-latency) $(OBJS-pincushion) $(OBJS-server)\
        $(OBJS-client)
LDFLAGS-$(d)client-test += $(PG_LDFLAGS) -lrt
TEST_BINS += $(d)client-test
