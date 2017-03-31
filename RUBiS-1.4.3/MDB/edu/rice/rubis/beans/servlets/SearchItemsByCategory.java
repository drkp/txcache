package edu.rice.rubis.beans.servlets;

import java.io.IOException;
import java.net.URLEncoder;

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

/** This servlets displays a list of items belonging to a specific category.
 * It must be called this way :
 * <pre>
 * http://..../SearchItemsByCategory?category=xx&categoryName=yy 
 *    where xx is the category id
 *      and yy is the category name
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class SearchItemsByCategory extends HttpServlet
{ 

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Search Items By Category");
    sp.printHTML("<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
  }

  private void itemList(Integer categoryId, int page, int nbOfItems, ServletPrinter sp, Context initialContext, String categoryName)
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
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicSearchItemsByCategory");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_SearchItemsByCategory : " +e+"<br>");
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
      message.setInt("page", page);
      message.setInt("nbItems", nbOfItems);
      message.setJMSCorrelationID("searchByCategory");
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
          sp.printHTML("<h2>Sorry, but there are no items available in this category !</h2>");
        else
        {
          sp.printHTML("<h2>Sorry, but there are no more items available in this category !</h2>");
          //          sp.printItemHeader();
          sp.printItemFooter("<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page-1)+"&nbOfItems="+nbOfItems+"\">Previous page</a>", "");
        }
        return ;
      }
      if (page == 0)
        sp.printItemFooter("", "<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page+1)+"&nbOfItems="+nbOfItems+"\">Next page</a>");
      else
        sp.printItemFooter("<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page-1)+"&nbOfItems="+nbOfItems+"\">Previous page</a>",
                           "<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page+1)+"&nbOfItems="+nbOfItems+"\">Next page</a>");
    } 
    catch (Exception e) 
    {
      printError("Exception getting item list: " + e +"<br>", sp);
    }
  }


  /**
   * Describe <code>doGet</code> method here.
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
    String categoryName;
    String  value;
    Integer categoryId;
    Integer page;
    Integer nbOfItems;

    categoryName = request.getParameter("categoryName");
    sp = new ServletPrinter(response, "SearchItemsByCategory");

    value = request.getParameter("category");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a category identifier!<br>", sp);
      return ;
    }
    else
      categoryId = new Integer(value);

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

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

    if (categoryName == null)
    {
      sp.printHTMLheader("RUBiS: Missing category name");
      sp.printHTML("<h2>Items in this category</h2><br><br>");
    }
    else
    {
      sp.printHTMLheader("RUBiS: Items in category "+categoryName);
      sp.printHTML("<h2>Items in category "+categoryName+"</h2><br><br>");
    }

    itemList(categoryId, page.intValue(), nbOfItems.intValue(), sp, initialContext, categoryName);
		
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
