#!/bin/sh

. $JONAS_ROOT/bin/unix/config_env
CLASSPATH=${CLASSPATH}:.:`pwd`/rubis.jar:`pwd`/rubis_ejb_servlets.jar
export CLASSPATH

### JBoss + RMI ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 -Djava.naming.factory.url.pkgs=org.jboss.naming"

### JBoss + JNP ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.jnp.interfaces.NamingContextFactory -Djava.naming.provider.url=localhost -Djava.naming.factory.url.pkgs=org.jboss.naming:org.jnp.interfaces"

### Jonas + rmi ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

### Jonas + jeremie ###
export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.objectweb.jeremie.libs.services.registry.jndi.JRMIInitialContextFactory -Djava.naming.provider.url=jrmi://localhost:12340 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

/opt/jakarta-tomcat-3.2.3/bin/startup.sh
