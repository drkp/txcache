d := $(dir $(lastword $(MAKEFILE_LIST)))

# TEST_SRCS += $(addprefix $(d), \
# 	pincushion-test.c)
# CFLAGS-$(d)pincushion-test.c += $(PG_CFLAGS)

# $(d)pincushion-test: $(o)pincushion-test.o $(LIB-test) $(OBJS-pincushion)
# LDFLAGS-$(d)pincushion-test += $(PG_LDFLAGS)

# TEST_BINS += $(d)pincushion-test
