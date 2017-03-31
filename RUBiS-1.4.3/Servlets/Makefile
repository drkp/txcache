# Main Makefile #

include ../config.mk

#########################
#    Servlets version   #
#########################

Servlets = ServletPrinter Config TimeManagement BrowseCategories Auth RegisterUser RubisHttpServlet \
	BrowseRegions SearchItemsByCategory SearchItemsByRegion ViewItem ViewBidHistory \
	ViewUserInfo SellItemForm RegisterItem PutCommentAuth PutComment StoreComment \
	BuyNowAuth BuyNow StoreBuyNow PutBidAuth PutBid StoreBid AboutMe

all_servlets_sources =  $(addprefix edu/rice/rubis/servlets/, $(addsuffix .java, $(Servlets)))
all_servlets_obj = $(addprefix edu/rice/rubis/servlets/, $(addsuffix .class, $(Servlets)))

servlets: $(all_servlets_obj)
	${JAR} cvf rubis_servlets.jar $(all_servlets_obj)

all: servlets

javadoc :
	${MKDIR} -p doc
	${JAVADOC} -d ./doc -bootclasspath ${CLASSPATH} -version -author -windowtitle "RUBiS Servlets API" -header "<b>RUBiS (C)2001 Rice University/INRIA</b><br>"  edu.rice.rubis.servlets

clean:
	${RM} -Rf edu/rice/rubis/servlets/*.class doc/* rubis_servlets.jar
