#!/bin/sh

XTRA_CLASSPATH=.:`pwd`/edu/rice/rubis:`pwd`/edu/rice/rubis/servlets
export XTRA_CLASSPATH
export J2SE_PREEMPTCLOSE=1

#registry &
EJBServer &