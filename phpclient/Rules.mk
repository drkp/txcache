d := $(dir $(lastword $(MAKEFILE_LIST)))

MY_SRCS = $(addprefix $(d), \
	txcache.c message.c marshal.c)
SRCS += $(MY_SRCS)
$(call add-CFLAGS,$(MY_SRCS),$(PHP_CFLAGS) $(PG_CFLAGS))

$(d)txcache.so: $(patsubst %.o,%-pic.o,$(o)txcache.o $(o)marshal.o $(o)message.o $(OBJS-client))
LDFLAGS-$(d)txcache.so += $(PHP_LDFLAGS)
LDFLAGS-$(d)txcache.so += -lrt

BINS += $(d)txcache.so

.PHONY: phpclient-check
phpclient-check: $(d)txcache.so
	support/list-dangling-syms $< `php-config --php-binary` | grep -v '@@GLIB'

d-phpclient := $(d)
.PHONY: phpclient-install
phpclient-install:
	rm -f `php-config --extension-dir`/txcache.so
	ln -sf `pwd`/$(d-phpclient)txcache.so `php-config --extension-dir`/txcache.so
	rm -f /etc/php5/conf.d/txcache.ini
	ln -sf `pwd`/$(d-phpclient)txcache.ini /etc/php5/conf.d/txcache.ini
	@echo
	@echo You should reload apache with something like
	@echo  /etc/init.d/apache2 reload
	@echo
