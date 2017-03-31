package edu.rice.rubis.beans.servlets;

import java.io.IOException;

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
 * Builds the html page with the list of all region in the database
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class BrowseRegions extends HttpServlet
{

  /**
   * Display the list of regions
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    String html;
    sp = new ServletPrinter(response, "BrowseRegions");
    sp.printHTMLheader("RUBiS: Available regions");
 
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      sp.printHTML("Cannot get initial context for JNDI: " +e+"<br>");
      return ;
    }
    TopicConnectionFactory topicFactory = null;
    TopicConnection connection = null;
    TopicSession session  = null;
    Topic topic = null;
    try 
    {
      // lookup the connection factory
      topicFactory = (TopicConnectionFactory)initialContext.lookup(Config.TopicConnectionFactoryName);
      // create a connection to the JMS provider
      connection = topicFactory.createTopicConnection();
      // lookup the destination
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicBrowseRegions");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_BrowseRegions : " +e+"<br>");
      return ;
    }
    try 
    {
      // create a requestor to receive the reply
      TopicRequestor requestor = new TopicRequestor(session, topic);
      // create a message
      TextMessage message = session.createTextMessage();
      message.setText("pouet");
      message.setJMSCorrelationID("region");
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
      sp.printHTML("Error contacting the message bean: " +e+"<br>");
      return ;
    }
    try 
    {
      if (html.equals(""))
        sp.printHTML("<h2>Sorry, but there is no region available at this time. Database table is empty</h2><br>");
      else
      {
        sp.printHTML("<h2>Currently available regions</h2><br>");
        sp.printHTML(html);
      }
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot get the list of regions: " +e+"<br>");
      return ;
    }
    sp.printHTMLfooter();
  }

}
