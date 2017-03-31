d := $(dir $(lastword $(MAKEFILE_LIST)))

MY_SRCS := $(addprefix $(d), \
	main.c invald.c)
SRCS += $(MY_SRCS)
$(call add-CFLAGS,$(MY_SRCS),$(PG_CFLAGS))

OBJS-invald := $(o)invald.o \
            $(LIB-rpcc) $(LIB-message) $(LIB-dbconn) $(OBJS-serverproxy)

$(d)invald: $(o)main.o  $(OBJS-invald)
LDFLAGS-$(d)invald += $(PG_LDFLAGS)
BINS += $(d)invald
