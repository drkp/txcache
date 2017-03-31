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

/** This servlets display the page allowing a user to put a comment
 * on an item.
 * It must be called this way :
 * <pre>
 * http://..../PutComment?to=ww&itemId=xx&nickname=yy&password=zz
 *    where ww is the id of the user that will receive the comment
 *          xx is the item id
 *          yy is the nick name of the user
 *          zz is the user password
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */


public class PutComment extends HttpServlet
{

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: PutComment");
    sp.printHTML("<h2>Your request has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
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
    String toStr = request.getParameter("to");
    String itemStr = request.getParameter("itemId");
    String username = request.getParameter("nickname");
    String password = request.getParameter("password");
    sp = new ServletPrinter(response, "PutComment");

    if ((toStr == null) || (toStr.equals("")))
    {
      printError("<h3>You must provide a 'to user' identifier !<br></h3>", sp);
      return ;
    }
    if ((itemStr == null) || (itemStr.equals("")))
    {
      printError("<h3>A valid item identifier is required!<br></h3>", sp);
      return ;
    }
        if ((username == null) || (username.equals("")))
    {
      printError("<h3>You must provide a username !<br></h3>", sp);
      return ;
    }
        if ((password == null) || (password.equals("")))
    {
      printError("<h3>You must provide a password !<br></h3>", sp);
      return ;
    }

    Integer itemId = new Integer(itemStr);
    Integer toId = new Integer(toStr);

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
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicPutComment");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_PutComment : " +e+"<br>");
      return ;
    }

    try 
    {
      sp.printHTMLheader("RUBiS: Comment service");
      // create a requestor to receive the reply
      TopicRequestor requestor = new TopicRequestor(session, topic);
      // create a message
      MapMessage message = session.createMapMessage();
      // set parameters
      message.setInt("itemId", itemId.intValue());
      message.setInt("toId", toId.intValue());
      message.setString("username", username);
      message.setString("password", password);
      message.setJMSCorrelationID("putComment");
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
      sp.printHTML("Cannot get the html form: " +e+"<br>");
      return ;
    }
    // Display the comment form
    sp.printHTML(html);
    sp.printHTML("<tr><td><b>Rating</b>\n"+
                 "<td><SELECT name=rating>\n"+
                 "<OPTION value=\"5\">Excellent</OPTION>\n"+
                 "<OPTION value=\"3\">Average</OPTION>\n"+
                 "<OPTION selected value=\"0\">Neutral</OPTION>\n"+
                 "<OPTION value=\"-3\">Below average</OPTION>\n"+
                 "<OPTION value=\"-5\">Bad</OPTION>\n"+
                 "</SELECT></table><p><br>\n"+
                 "<TEXTAREA rows=\"20\" cols=\"80\" name=\"comment\">Write your comment here</TEXTAREA><br><p>\n"+
                 "<input type=submit value=\"Post this comment now!\"></center><p>\n");
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
