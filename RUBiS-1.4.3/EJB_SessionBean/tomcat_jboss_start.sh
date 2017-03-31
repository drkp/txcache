#!/bin/sh

CLASSPATH=${JBOSS_DIST}/client/jnp-client.jar:${JBOSS_DIST}/client/jnet.jar:${JBOSS_DIST}/client/jbossall-client.jar:${JBOSS_DIST}/client/jboss-j2ee.jar:/users/margueri/RUBiS/EJB_SessionBean/rubis.jar
export CLASSPATH

### JBoss + JNP ###
export CATALINA_OPTS="-Xmx512m -Xss96k -Djava.naming.factory.initial=org.jnp.interfaces.NamingContextFactory -Djava.naming.provider.url=localhost -Djava.naming.factory.url.pkgs=org.jboss.naming:org.jnp.interfaces"

#/opt/jakarta-tomcat-3.2.3/bin/startup.sh
/opt/jakarta-tomcat-4.1.24/bin/startup.sh