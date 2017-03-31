#!/bin/sh

. $JONAS_ROOT/bin/unix/config_env
CLASSPATH=${CLASSPATH}:.:/users/margueri/RUBiS/EJB_MDB/rubis.jar:/users/margueri/RUBiS/MDB/rubis_ejb_servlets.jar:
export CLASSPATH

### Jonas + rmi ###
export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://sci20:1099 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

### Jonas + jeremie ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.objectweb.jeremie.libs.services.registry.jndi.JRMIInitialContextFactory -Djava.naming.provider.url=jrmi://sci20:12340 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

/opt/jakarta-tomcat-3.2.3/bin/startup.sh
