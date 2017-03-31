d := $(dir $(lastword $(MAKEFILE_LIST)))

#
# check-based tests
#

TEST_SRCS += $(addprefix $(d), \
	rbtree-test.c iobuf-test.c pinset-test.c)

$(d)rbtree-test: $(o)rbtree-test.o $(LIB-test)

$(d)iobuf-test: $(o)iobuf-test.o $(LIB-test) $(LIB-iobuf)

$(d)pinset-test: $(o)pinset-test.o $(LIB-test) $(LIB-pinset)

TEST_BINS += $(d)rbtree-test $(d)iobuf-test $(d)pinset-test

#
# Test programs
#

SRCS += $(addprefix $(d), \
	rpcc-test.c rpcs-test.c)

$(d)rpcc-test: $(o)rpcc-test.o $(LIB-rpcc)

$(d)rpcs-test: $(o)rpcs-test.o $(LIB-rpcs)

BINS += $(d)rpcc-test $(d)rpcs-test
