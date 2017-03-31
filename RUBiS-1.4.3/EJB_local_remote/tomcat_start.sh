#!/bin/sh

. $JONAS_ROOT/bin/unix/config_env

# classpath for JOnAS
# CLASSPATH=${CLASSPATH}:.:`pwd`/rubis.jar:`pwd`/rubis_ejb_servlets.jar

# classpath for JBoss 3.0
CLASSPATH=${CLASSPATH}:.:`pwd`/rubis.jar:`pwd`/rubis_ejb_servlets.jar:/usr/java/jboss-3.0.0/client/jnet.jar:/usr/java/jboss-3.0.0/client/jboss-j2ee.jar:/usr/java/jboss-3.0.0/client/jboss-client.jar:/usr/java/jboss-3.0.0/client/jbosssx-client.jar:/usr/java/jboss-3.0.0/client/jnp-client.jar:/usr/java/jboss-3.0.0/client/jboss-common-client.jar:/usr/java/jboss-3.0.0/client/log4j.jar
export CLASSPATH

### JBoss + RMI ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 -Djava.naming.factory.url.pkgs=org.jboss.naming"

### JBoss + JNP ###
export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.jnp.interfaces.NamingContextFactory -Djava.naming.provider.url=localhost -Djava.naming.factory.url.pkgs=org.jboss.naming:org.jnp.interfaces"

### Jonas + rmi ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

### Jonas + jeremie ###
#export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.objectweb.jeremie.libs.services.registry.jndi.JRMIInitialContextFactory -Djava.naming.provider.url=jrmi://localhost:12340 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

/opt/jakarta-tomcat-3.2.3/bin/startup.sh
