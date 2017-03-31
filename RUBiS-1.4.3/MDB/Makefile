# Main Makefile #

include ../config.mk

####################
#    EJB version   #
####################

MessageBeans = MDB_Auth MDB_BrowseRegions MDB_BrowseCategories \
	MDB_SearchItemsByCategory MDB_SearchItemsByRegion MDB_ViewItem MDB_ViewUserInfo \
	MDB_PutComment MDB_StoreComment MDB_RegisterUser MDB_RegisterItem \
	MDB_ViewBidHistory MDB_PutBid MDB_StoreBid \
	MDB_BuyNow MDB_StoreBuyNow MDB_AboutMe

all_beans_sources =  $(addprefix edu/rice/rubis/beans/, $(addsuffix .java, $(MessageBeans))) edu/rice/rubis/beans/TimeManagement.java edu/rice/rubis/beans/BeanConfig.java
all_beans_obj = $(addprefix edu/rice/rubis/beans/, $(addsuffix .class, $(MessageBeans))) edu/rice/rubis/beans/TimeManagement.class edu/rice/rubis/beans/BeanConfig.class

# Common build

beans_xml = META-INF/ejb-jar.xml

#config_ejb:
#	${CP} edu/rice/rubis/beans/QueryBean${DB_SERVER}.java edu/rice/rubis/beans/QueryBean.java
# config_ejb 
beans: $(all_beans_obj)

base-jar:
	${RM} -f rubis.jar
	${JAR} cvf rubis.jar $(all_beans_obj)

# JOnAS specific build

jonas_xml = ejb-jar.xml jonas-ejb-jar.xml

%.xml: Makefile 
	${GENIC} -rmiopts -d . -javac $(JAVAC) -javacopts $(JAVACOPTS) -verbose $@

jonas-jar: META-INF/JOnAS/ejb-jar.xml base-jar
	${CP} $(addprefix META-INF/JOnAS/, $(jonas_xml)) META-INF/
	${JAR} uvf rubis.jar $(addprefix META-INF/, $(jonas_xml)) edu/rice/rubis/beans/JOnAS*.class
	${RM} -f $(addprefix META-INF/, $(jonas_xml)) edu/rice/rubis/beans/JOnAS*.class

clean_jonas:
	touch Makefile

# JBoss specific build

jboss_xml = ejb-jar.xml jboss.xml

jboss-jar: base-jar
	${CP} $(addprefix META-INF/JBoss/, $(jboss_xml)) META-INF/
	${JAR} uvf rubis.jar $(addprefix META-INF/, $(jboss_xml))
	${RM} -f $(addprefix META-INF/, $(jboss_xml))

clean_jboss:


##################
#  EJB Servlets  #
##################

EJB_Servlets = ServletPrinter Config BrowseRegions BrowseCategories \
	   SearchItemsByCategory SearchItemsByRegion ViewItem ViewUserInfo \
	   PutCommentAuth PutComment StoreComment RegisterUser \
	   RegisterUser RegisterItem SellItemForm \
	   PutBidAuth PutBid ViewBidHistory StoreBid \
	   BuyNow BuyNowAuth StoreBuyNow AboutMe


all_ejb_servlets_sources =  $(addprefix edu/rice/rubis/beans/servlets/, $(addsuffix .java, $(EJB_Servlets)))
all_ejb_servlets_obj = $(addprefix edu/rice/rubis/beans/servlets/, $(addsuffix .class, $(EJB_Servlets)))

#config_$(EJB_SERVER)
ejb_servlets: $(all_ejb_servlets_obj)
	${JAR} cvf rubis_ejb_servlets.jar $(all_ejb_servlets_obj)


##################
#  Global rules  #
##################

config_jonas:
	${CP} edu/rice/rubis/beans/servlets/ConfigJOnAS.java edu/rice/rubis/beans/servlets/Config.java
	${CP} edu/rice/rubis/beans/BeanConfigJOnAS.java edu/rice/rubis/beans/BeanConfig.java

config_jboss:
	${CP} edu/rice/rubis/beans/servlets/ConfigJBoss.java edu/rice/rubis/beans/servlets/Config.java
	${CP} edu/rice/rubis/beans/BeanConfigJBoss.java edu/rice/rubis/beans/BeanConfig.java

all: ejb_servlets beans $(EJB_SERVER)-jar

clean: clean_$(EJB_SERVER)
	${RM} -Rf edu/rice/rubis/beans/servlets/*.class edu/rice/rubis/beans/*.class doc/* rubis_ejb_servlets.jar rubis.jar

javadoc :
	${MKDIR} -p doc
	${JAVADOC} -d ./doc -bootclasspath ${CLASSPATH} -version -author -windowtitle "RUBiS EJB with EntityBean managed ids version API" -header "<b>RUBiS (C)2001 Rice University/INRIA</b><br>" edu.rice.rubis.beans edu.rice.rubis.beans.servlets
