#!/bin/sh

CLASSPATH=${CLASSPATH}:`pwd`/rubis_ejb_servlets.jar:/usr/java/JBoss-2.4.4/lib/ext/jboss.jar:/usr/java/JBoss-2.4.4/lib/ext/jnpserver.jar:/usr/java/JBoss-2.4.4/lib/ext/jndi.jar:/usr/java/JBoss-2.4.4/client/jboss-client.jar:/usr/java/JBoss-2.4.4/client/jnp-client.jar:
export CLASSPATH

### JBoss + JNP ###
export TOMCAT_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.jnp.interfaces.NamingContextFactory -Djava.naming.provider.url=localhost -Djava.naming.factory.url.pkgs=org.jboss.naming:org.jnp.interfaces"

/opt/jakarta-tomcat-3.2.3/bin/startup.sh
