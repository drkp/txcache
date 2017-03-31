#!/bin/sh

cd /usr/local/mysql
bin/safe_mysqld --user=mysql --set-variable max_connections=800 &
