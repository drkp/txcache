#ifndef _PHPCLIENT_TXCACHE_H_
#define _PHPCLIENT_TXCACHE_H_

#include <php.h>

#define PHP_TXCACHE_VERSION "1.0"
#define PHP_TXCACHE_EXTNAME "txcache"

extern zend_module_entry txcache_entry;

#ifdef ZTS
#include <TSRM.h>
#endif

ZEND_BEGIN_MODULE_GLOBALS(txcache)
    long pcounter;
    long long lastStatsFlush;
ZEND_END_MODULE_GLOBALS(txcache)

#ifdef ZTS
#define TG(v) TSRMG(txcache_globals_id, zend_txcache_globals *, v)
#else
#define TG(v) (txcache_globals.v)
#endif

#endif // _PHPCLIENT_TXCACHE_H_
