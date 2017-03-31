#!/bin/sh

# JVM options for Jrockit
#JAVA_OPTS="-Xnoopt -Xms128m -Xmx778m -Xss64k"

# JVM options for sun jdk
#JAVA_OPTS="-server -Xms128m -Xmx778m -Xss64k"

JAVA_OPTS="-Xms128m -Xmx768m -Xss16k"

export JAVA_OPTS

# jonas<2.6
#XTRA_CLASSPATH=.:`pwd`/edu/rice/rubis:`pwd`/edu/rice/rubis/servlets
#export XTRA_CLASSPATH
#EJBServer &

# since jonas 2.6
jonas start