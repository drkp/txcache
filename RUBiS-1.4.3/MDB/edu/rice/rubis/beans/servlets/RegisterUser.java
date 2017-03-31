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

/** This servlet register a new user in the database and display
 * the result of the transaction.
 * It must be called this way :
 * <pre>
 * http://..../RegisterUser?firstname=aa&lastname=bb&nickname=cc&email=dd&password=ee&region=ff
 *   where: aa is the user first name
 *          bb is the user last name
 *          cc is the user nick name (login name)
 *          dd is the email address of the user
 *          ee is the user password
 *          ff is the identifier of the region where the user lives
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class RegisterUser extends HttpServlet
{

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Register user");
    sp.printHTML("<h2>Your registration has not been processed due to the following error :</h2><br>");
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
    String firstname=null, lastname=null, nickname=null, email=null, password=null;
    int    regionId = 0;
    int    userId;
    String creationDate, regionName;

    sp = new ServletPrinter(response, "RegisterUser");
      
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

    String value = request.getParameter("firstname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a first name!<br>", sp);
      return ;
    }
    else
      firstname = value;

    value = request.getParameter("lastname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a last name!<br>", sp);
      return ;
    }
    else
      lastname = value;

    value = request.getParameter("nickname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a nick name!<br>", sp);
      return ;
    }
    else
      nickname = value;

    value = request.getParameter("email");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide an email address!<br>", sp);
      return ;
    }
    else
      email = value;

    value = request.getParameter("password");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a password!<br>", sp);
      return ;
    }
    else
      password = value;


    value = request.getParameter("region");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a valid region!<br>", sp);
      return ;
    }
    else
      regionName = value;

    // Try to create a new user
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
      topic = (Topic) initialContext.lookup(Config.PrefixTopicName+"topicRegisterUser");
      // create a session
      session  = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot connect to message bean MDB_RegisterUser : " +e+"<br>");
      return ;
    }
    try 
    {
      // create a requestor to receive the reply
      TopicRequestor requestor = new TopicRequestor(session, topic);
      // create a message
      MapMessage message = session.createMapMessage();
      // set parameters
      message.setString("firstname", firstname);
      message.setString("lastname", lastname);
      message.setString("nickname", nickname);
      message.setString("email", email);
      message.setString("password", password);
      message.setString("region", regionName);
      message.setJMSCorrelationID("registerUser");
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
      sp.printHTML("User registration failed: " +e+"<br>");
      return ;
    }
    sp.printHTMLheader("RUBiS: Welcome to "+nickname);

    sp.printHTML("<h2>Your registration has been processed successfully</h2><br>\n");
    sp.printHTML("<h3>Welcome "+nickname+"</h3>\n");
    sp.printHTML("RUBiS has stored the following information about you:<br>\n");
    sp.printHTML("First Name : "+firstname+"<br>\n");
    sp.printHTML("Last Name  : "+lastname+"<br>\n");
    sp.printHTML("Nick Name  : "+nickname+"<br>\n");
    sp.printHTML("Email      : "+email+"<br>\n");
    sp.printHTML("Password   : "+password+"<br>\n");
    sp.printHTML("Region     : "+regionName+"<br>\n");
    sp.printHTML("<br>The following information has been automatically generated by RUBiS:<br>\n");
    sp.printHTML(html);
    sp.printHTMLfooter();

  }
    
 
  /**
   * Call the <code>doGet</code> method here.
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
