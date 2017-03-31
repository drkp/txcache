#!/bin/sh

. $JONAS_ROOT/bin/unix/config_env


CLASSPATH=${JONAS_ROOT}/lib/common/jmx/jmx/jmxri.jar:${JONAS_ROOT}/lib/client.jar:${JONAS_ROOT}/lib/common/carol/carol.jar:/home/margueri/workspace/RUBiS/EJB_CMP2.0/rubis.jar

export CLASSPATH


### Jonas + rmi ###
export CATALINA_OPTS="-Xmx512m -Xss128k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

### Jonas + jeremie ###
#export CATALINA_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.objectweb.jeremie.libs.services.registry.jndi.JRMIInitialContextFactory -Djava.naming.provider.url=jrmi://sci20:12340 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

/opt/jakarta-tomcat-4.1.24/bin/startup.sh
