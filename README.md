# TxCache: a transactional, self-managing application-level cache

This is the implementation of the TxCache system, as described in the
paper
["Transactional Consistency and Automatic Management in an Application Data Cache"](https://drkp.net/papers/txcache-osdi10.pdf)
from OSDI 2010.

TxCache is a transactional, self-managing application-level cache. It
can be used for many of the same purposes as memcached, but provides a
simple programming model. TxCache ensures that any data seen within a
transaction, whether it comes from the cache or the database, reflects
a consistent snapshot of the database. For additional performance,
this snapshot may be slightly stale (but still consistent). TxCache
makes it easy to add caching to an application by simply designating
functions as cacheable; it automatically caches their results, and
invalidates the cached data as the underlying database changes.

TxCache introduces the following mechanisms:

* a protocol for ensuring that transactions see only consistent cached
  data, using minor database modifications to compute the validity
  times of database queries, and attaching them to cache objects.

* a lazy timestamp selection algorithm that assigns a transaction to a
  timestamp in the recent past based on the availability of cached
  data.
  
* an automatic invalidation system that tracks each object’s database
  dependencies using dual-granularity invalidation tags, and produces
  notifications if they change.
  
More information about the design of TxCache is available in the
[OSDI 2010 paper](https://drkp.net/papers/txcache-osdi10.pdf)
mentioned above, as well as in the following Ph.D. thesis:
["Application-Level Caching with Transactional Consistency"](https://drkp.net/papers/thesis.pdf)
(but who has time to read those?)

## Contents

This repository contains the following parts of the TxCache system:

- src/ - the TxCache core system, including:
    - client/ - the language-independent parts of the cache client
      bindings
    - invald/ - a daemon for receiving invalidation notifications for
      the database and distributing them to cache servers
    - lib/ - common support libraries
    - phpclient/ - PHP-specific cache client bindings
    - server/ - the cache server
    - support/ - miscellaneous tools, mostly for gathering cache usage
      statistics
- pgsql/ - a modified version of PostgreSQL 8.2.11 that provides the
  necessary support, including validity interval tracking,
  invalidation generation, and explicit control of query snapshots
- RUBiS-1.4.3/ - an adaptation of the
  [RUBiS benchmark](http://rubis.ow2.org/) as used for evaluation in
  the OSDI paper

## Building and Running

The cache client and server can be built using `make` in the `src`
directory. Regression tests can be run with `make check`.

The PHP bindings require PHP 5.x so an older OS like Ubuntu 12.04
might be the best way to get this running today. (It probably wouldn't
be too much trouble to port them to PHP 7, however.) The rest of the
code has also been tested on Ubuntu 16.04 and Debian 8.

Dependencies include (Debian/Ubuntu packages): 
  check libpq-dev php5-dev

For performance measurements, you will likely want to add `-DNASSERT`
and `-O2` to the `CFLAGS` in the Makefile, and run `make PARANOID=0`,
which disables complexity-changing assertions.

Instructions for building and running the modified version of
PostgreSQL, along with its interface, are in
[pgsql/README.TXCACHE](pgsql/README.TXCACHE).

## Contact

Please email Dan Ports at drkp@cs.washington.edu with any questions.
