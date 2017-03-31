package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.jms.MapMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicRequestor;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Build the html page with the list of all items for given category and region.
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class SearchItemsByRegion extends HttpServlet
{
 
  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: SearchItemsByRegion");
    sp.printHTML("<h2>Your request has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }


  /** List items in the given category for the given region */
  private void itemList(Integer categoryId, Integer regionId, int page, int nbOfItems, ServletPrinter sp, Context initialContext) 
  {
    TopicConnectionFactory topicFactory = null;
    TopicConnection connection = null;
    TopicSession session  = null;
    Topic topic = null;
    String list;
    try 
    {
      // lookup the connection factory
      topicFactory = (TopicConnectionFactory)initialContext.lookup(Config.TopicConnectionFactoryName);
      // create a connection to the JMS provider
      connection = topicFactory.createTopicConnection();
      // lookup the destination
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicSearchItemsByRegion");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_SearchItemsByRegion : " +e+"<br>");
      return ;
    }
    try 
    {
      // create a requestor to receive the reply
      TopicRequestor requestor = new TopicRequestor(session, topic);
      // create a message
      MapMessage message = session.createMapMessage();
      // set parameters
      message.setInt("categoryId", categoryId.intValue());
      message.setInt("regionId", regionId.intValue());
      message.setInt("page", page);
      message.setInt("nbItems", nbOfItems);
      message.setJMSCorrelationID("searchByRegion");
      // send the message and receive the reply
      connection.start(); // allows message to be delivered (default is connection stopped)
      TextMessage reply = (TextMessage)requestor.request(message);
      connection.stop();
      // read the reply
      list = reply.getText();
      // close connection and session
      requestor.close(); // also close the session
      connection.close();
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot get the list of items: " +e+"<br>");
      return ;
    }
    try
    {
      if ((list != null) && (!list.equals("")))
      {
        sp.printItemHeader();
        sp.printHTML(list); 
        sp.printItemFooter();
      }
      else
      {
        if (page == 0)
          sp.printHTML("<h3>Sorry, but there is no items in this category for this region.</h3><br>");
        else
        {
          sp.printHTML("<h3>Sorry, but there is no more items in this category for this region.</h3><br>");
          sp.printItemHeader();
          sp.printItemFooter("<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="+categoryId+
                             "&region="+regionId+"&page="+(page-1)+"&nbOfItems="+nbOfItems+"\">Previous page</a>", "");
        }
        return ;
      }
      if (page == 0)
        sp.printItemFooter("", "<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="+categoryId+
                           "&region="+regionId+"&page="+(page+1)+"&nbOfItems="+nbOfItems+"\">Next page</a>");
      else
        sp.printItemFooter("<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="+categoryId+
                           "&region="+regionId+"&page="+(page-1)+"&nbOfItems="+nbOfItems+"\">Previous page</a>",
                           "<a href=\"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="+categoryId+
                           "&region="+regionId+"&page="+(page+1)+"&nbOfItems="+nbOfItems+"\">Next page</a>");
    } 
    catch (Exception e) 
    {
      sp.printHTML("Exception getting item list: " + e +"<br>");
    }
  }

  /** 
   * Read the parameters, lookup the remote category and region 
   * and build the web page with the list of items 
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    Context initialContext = null;

    Integer  categoryId, regionId;
    Integer page;
    Integer nbOfItems;

    sp = new ServletPrinter(response, "SearchItemsByRegion");

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

    String value = request.getParameter("category");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a category!<br>", sp);
      return ;
    }
    else
      categoryId = new Integer(value);

    value = request.getParameter("region");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a region!<br>", sp);
      return ;
    }
    else
      regionId = new Integer(value);

    value = request.getParameter("page");
    if ((value == null) || (value.equals("")))
      page = new Integer(0);
    else
      page = new Integer(value);

    value = request.getParameter("nbOfItems");
    if ((value == null) || (value.equals("")))
      nbOfItems = new Integer(25);
    else
      nbOfItems = new Integer(value);

    sp.printHTMLheader("RUBiS: Search items by region");
    sp.printHTML("<h2>Items in this region</h2><br><br>");
     
    itemList(categoryId, regionId, page.intValue(), nbOfItems.intValue(), sp, initialContext);
		
    sp.printHTMLfooter();
  }


  /**
   * Call the <code>doGet</code> method.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    doGet(request, response);
  }
}
