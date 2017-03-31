#!/bin/sh

# classpath jonas 2.6/ 3.x rmi
#CLASSPATH=${JONAS_ROOT}/lib/RMI_client.jar:${JONAS_ROOT}/lib/RMI_jonas.jar:/users/margueri/RUBiS/EJB_Session_facade/rubis.jar

# classpath jonas 2.6/ 3.x jeremie
#CLASSPATH=${JONAS_ROOT}/lib/JEREMIE_client.jar:${JONAS_ROOT}/lib/JEREMIE_jonas.jar:/users/margueri/RUBiS/EJB_Session_facade/rubis.jar

# classpath jonas 3.1 with carol
#CLASSPATH=${JONAS_ROOT}/lib/client.jar:${JONAS_ROOT}/lib/jonas.jar:${JONAS_ROOT}/lib/common/carol/carol.jar:/users/margueri/RUBiS/EJB_Session_facade/rubis.jar

# jonas 2.5
#. $JONAS_ROOT/bin/unix/config_env
#CLASSPATH=${CLASSPATH}:.:/users/cecchet/RUBiS/EJB_Session_facade/rubis.jar:/users/cecchet/RUBiS/EJB_Session_facade/rubis_ejb_servlets.jar:
#export CLASSPATH

### Jonas + rmi ###
export CATALINA_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

### Jonas + jeremie ###
#export CATALINA_OPTS="-Xmx512m -Xss16k -Djava.naming.factory.initial=org.objectweb.jeremie.libs.services.registry.jndi.JRMIInitialContextFactory -Djava.naming.provider.url=jrmi://sci20:12340 -Djava.naming.factory.url.pkgs=org.objectweb.jonas.naming"

#/opt/jakarta-tomcat-3.2.3/bin/startup.sh
/opt/jakarta-tomcat-4.1.24/bin/startup.sh