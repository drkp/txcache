#!/bin/sh

cd /usr/local/mysql
bin/safe_mysqld --user=mysql --set-variable max_connections=600 -O bdb_cache_size=256M -O bdb_log_buffer_size=1M -O key_buffer_size=256M -O bdb_max_lock=2000000 -O sort_buffer=32M &
