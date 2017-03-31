#!/bin/sh

XTRA_CLASSPATH=.:`pwd`/edu/rice/rubis:`pwd`/edu/rice/rubis/servlets:/usr/local/OptimizeitSuite/lib/optit.jar:${J2EE_HOME}/lib/j2ee.jar:${JAVA_HOME}/jre/lib/rt.jar
export XTRA_CLASSPATH

JAVA_OPTS="-Xms128m -Xmx768m -Xss32k -Xrunpri -Xbootclasspath/a:$JONAS_ROOT/config/:/usr/local/OptimizeitSuite/lib/oibcp.jar intuitive.audit.Audit -startCPUprofiler:type=instrumentation,filterEnabled=true,filterDelay=50 -offlineprofiling:initialDelay=4m,delay=10m,directory=/tmp,filename=JOnAS_SF,includeCPU=true,includeMemory=false"

export JAVA_OPTS
export LD_LIBRARY_PATH=/usr/local/OptimizeitSuite/lib/

#registry &
EJBServer &