package edu.rice.rubis.beans.servlets;

import java.io.IOException;
import java.util.GregorianCalendar;

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

import edu.rice.rubis.beans.TimeManagement;

/**
 * Add a new item in the database
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class RegisterItem extends HttpServlet
{

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Register user");
    sp.printHTML("<h2>Your registration has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /**
   * Check the values from the html register item form and create a new item
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    String  name=null, description=null;
    float   initialPrice, buyNow, reservePrice;
    Float   stringToFloat;
    int     quantity, duration;
    Integer categoryId, userId, stringToInt;
    String  startDate, endDate;
    int     itemId;

    sp = new ServletPrinter(response, "RegisterItem");
      
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

    String value = request.getParameter("name");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a name!<br>", sp);
      return ;
    }
    else
      name = value;

    value = request.getParameter("description");
    if ((value == null) || (value.equals("")))
    {
      description="No description";
    }
    else
      description = value;

    value = request.getParameter("initialPrice");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide an initial price!<br>", sp);
      return ;
    }
    else
    {
      stringToFloat = new Float(value);
      initialPrice = stringToFloat.floatValue();
    }

    value = request.getParameter("reservePrice");
    if ((value == null) || (value.equals("")))
    {
      reservePrice = 0;
    }
    else
    {
      stringToFloat = new Float(value);
      reservePrice = stringToFloat.floatValue();

    }

    value = request.getParameter("buyNow");
    if ((value == null) || (value.equals("")))
    {
      buyNow = 0;
    }
    else
    {
      stringToFloat = new Float(value);
      buyNow = stringToFloat.floatValue();
    }
 
    value = request.getParameter("duration");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a duration!<br>", sp);
      return ;
    }
    else
    {
      stringToInt = new Integer(value);
      duration = stringToInt.intValue();
      GregorianCalendar now, later;
      now = new GregorianCalendar();
      later = TimeManagement.addDays(now, duration);
      startDate = TimeManagement.dateToString(now);
      endDate = TimeManagement.dateToString(later);
    }

    value = request.getParameter("quantity");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a quantity!<br>", sp);
      return ;
    }
    else
    {
      stringToInt = new Integer(value);
      quantity = stringToInt.intValue();
    }
 
    userId = new Integer(request.getParameter("userId"));
    categoryId = new Integer(request.getParameter("categoryId"));

    TopicConnectionFactory topicFactory = null;
    TopicConnection connection = null;
    TopicSession session  = null;
    Topic topic = null;
    String html;
    try 
    {
      // lookup the connection factory
      topicFactory = (TopicConnectionFactory)initialContext.lookup(Config.TopicConnectionFactoryName);
      // create a connection to the JMS provider
      connection = topicFactory.createTopicConnection();
      // lookup the destination
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicRegisterItem");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_RegisterItem : " +e+"<br>");
      return ;
    }
    try 
    {
      // create a requestor to receive the reply
      TopicRequestor requestor = new TopicRequestor(session, topic);
      // create a message
      MapMessage message = session.createMapMessage();
      // set parameters
      message.setString("name", name);
      message.setString("description", description);
      message.setFloat("initialPrice", initialPrice);
      message.setInt("quantity", quantity);
      message.setFloat("reservePrice", reservePrice);
      message.setFloat("buyNow", buyNow);
      message.setString("startDate", startDate);
      message.setString("endDate", endDate);
      message.setInt("userId", userId.intValue());
      message.setInt("categoryId", categoryId.intValue());
      message.setJMSCorrelationID("registerItem");
      // send the message and receive the reply
      connection.start(); // allows message to be delivered (default is connection stopped)
      TextMessage reply = (TextMessage)requestor.request(message);
      connection.stop();
      // read the reply
      html = reply.getText();
      // close connection and session
      requestor.close(); // also close the session
      connection.close();
    } 
    catch (Exception e)
    {
      sp.printHTML("Item registration failed: " +e+"<br>");
      return ;
    }

    sp.printHTMLheader("RUBiS: Selling "+name);

    sp.printHTML("<center><h2>Your Item has been successfully registered.</h2></center><br>\n");
    sp.printHTML("<b>RUBiS has stored the following information about your item:</b><br><p>\n");
    sp.printHTML("<TABLE>\n");
    sp.printHTML("<TR><TD>Name<TD>"+name+"\n");
    sp.printHTML("<TR><TD>Description<TD>"+description+"\n");
    sp.printHTML("<TR><TD>Initial price<TD>"+initialPrice+"\n");
    sp.printHTML("<TR><TD>ReservePrice<TD>"+reservePrice+"\n");
    sp.printHTML("<TR><TD>Buy Now<TD>"+buyNow+"\n");
    sp.printHTML("<TR><TD>Quantity<TD>"+quantity+"\n");
    sp.printHTML("<TR><TD>Duration<TD>"+duration+"\n"); 
    sp.printHTML("</TABLE>\n");
    sp.printHTML("<br><b>The following information has been automatically generated by RUBiS:</b><br>\n");
    sp.printHTML("<TABLE>\n");
    sp.printHTML("<TR><TD>Start date<TD>"+startDate+"\n");
    sp.printHTML("<TR><TD>end date<TD>"+endDate+"\n");
    sp.printHTML("<TR><TD>User id<TD>"+userId+"\n");
    sp.printHTML("<TR><TD>Category id<TD>"+categoryId+"\n");
    sp.printHTML(html);
    sp.printHTML("</TABLE>\n");
    sp.printHTMLfooter();
   
  }
    
 
  /**
   * Call the doGet method: check the values from the html register item form 
   *	and create a new item
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
