d := $(dir $(lastword $(MAKEFILE_LIST)))

MY_SRCS := $(addprefix $(d), \
	main.c pincushion.c pintable.c checkdb.c)
SRCS += $(MY_SRCS)
$(call add-CFLAGS,$(MY_SRCS),$(PG_CFLAGS))

OBJS-pincushion := $(o)pincushion.o $(o)pintable.o $(o)checkdb.o\
	$(LIB-rpcs) $(LIB-message) $(LIB-dbconn)

$(d)pincushion: $(o)main.o $(OBJS-pincushion)
LDFLAGS-$(d)pincushion += $(PG_LDFLAGS)
BINS += $(d)pincushion

SRCS += $(d)proxy.c
OBJS-pincushionproxy := $(o)proxy.o

include $(d)tests/Rules.mk
