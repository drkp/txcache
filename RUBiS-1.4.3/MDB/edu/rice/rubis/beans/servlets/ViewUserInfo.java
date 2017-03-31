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

/** This servlets displays general information about a user.
 * It must be called this way :
 * <pre>
 * http://..../ViewUserInfo?userId=xx where xx is the id of the user
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class ViewUserInfo extends HttpServlet
{
  
  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: View user info");
    sp.printHTML("<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }


  /**
   * Call the <code>doPost</code> method.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    doPost(request, response);
  }

  /**
   * Display information about a user.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    Context initialContext = null;

    String  value = request.getParameter("userId");
    Integer userId;
    
    sp = new ServletPrinter(response, "ViewUserInfo");

    if ((value == null) || (value.equals("")))
    {
      sp.printHTMLheader("RUBiS ERROR: View user information");
      sp.printHTML("<h3>You must provide a user identifier !<br></h3>");
      sp.printHTMLfooter();
      return ;
    }
    else
      userId = new Integer(value);

    sp.printHTMLheader("RUBiS: View user information");

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

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
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicViewUserInfo");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_ViewUserInfo : " +e+"<br>");
      return ;
    }
    try 
    {
      Integer itemId = new Integer(value);
      // create a requestor to receive the reply
      TopicRequestor requestor = new TopicRequestor(session, topic);
      // create a message
      MapMessage message = session.createMapMessage();
      // set parameters
      message.setInt("userId", userId.intValue());
      message.setJMSCorrelationID("viewUserId");
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
      sp.printHTML("Cannot get user description: " +e+"<br>");
      return ;
    }
    sp.printHTML(html); 	
    sp.printHTMLfooter();

  }

}
