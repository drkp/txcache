d := $(dir $(lastword $(MAKEFILE_LIST)))

MY_SRCS := $(addprefix $(d), \
	client.c policy.c)
SRCS += $(MY_SRCS)
$(call add-CFLAGS,$(MY_SRCS),$(PG_CFLAGS))

OBJS-client := $(o)client.o $(o)policy.o $(OBJS-pincushionproxy) \
	$(OBJS-serverproxy) \
	$(LIB-rpcc) $(LIB-pinset) $(LIB-hash) $(LIB-latency)

include $(d)tests/Rules.mk
