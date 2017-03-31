package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.EJBException;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.Serializable;

/**
 * This is a stateless session bean used to get the information to build the html form
 * used to put a comment on a user. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_PutComment implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_PutComment()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      int itemId = request.getInt("itemId");
      int toId = request.getInt("toId");
      String username = request.getString("username");
      String password = request.getString("password");  

        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // get the post comment form
      String html = getCommentForm(itemId, toId, username, password);

      // send the reply
      TemporaryTopic temporaryTopic = (TemporaryTopic) request.getJMSReplyTo();
      if (temporaryTopic != null)
      {
        // create a connection
        connection = connectionFactory.createTopicConnection();
        // create a session: no transaction, auto ack
        session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TextMessage reply = session.createTextMessage();
        reply.setJMSCorrelationID(correlationID);
        reply.setText(html);
        replier = session.createPublisher(null); // unidentified publisher
        connection.start();
        replier.publish(temporaryTopic, reply);
        replier.close();
        session.close();
        connection.stop();
        connection.close();
      }
    }
    catch (Exception e)
    {
      throw new EJBException("Message traitment failed for MDB_PutComment: " +e);
    }
  }


  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getCommentForm(int itemId, int toId, String username, String password) throws RemoteException 
  {
    int userId             = -1;
    StringBuffer html      = new StringBuffer();
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

    // Authenticate the user who want to comment
      if ((username != null && !username.equals("")) || (password != null && !password.equals("")))
      {
        TopicConnection authConnection;
        TopicSession authSession;
        Topic authTopic;
        try 
        {
          // create a connection
          authConnection = connectionFactory.createTopicConnection();
          // lookup the destination
          authTopic = (Topic) initialContext.lookup(BeanConfig.PrefixTopicName+"topicAuth");
          // create a session
          authSession  = authConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
        } 
        catch (Exception e)
        {
          throw new EJBException("Cannot connect to message bean MDB_Auth : " +e+"<br>");
        }
        try 
        {
          // create a requestor to receive the reply
          TopicRequestor requestor = new TopicRequestor(authSession, authTopic);
          // create a message
          MapMessage m = authSession.createMapMessage();
          // set parameters
          m.setStringProperty("nickname", username);
          m.setStringProperty("password", password);
          m.setJMSCorrelationID("auth");
          // send the message and receive the reply
          authConnection.start(); // allows message to be delivered (default is connection stopped)
          MapMessage authReply = (MapMessage)requestor.request(m);
          authConnection.stop();
          // read the reply
          userId = authReply.getInt("userId");
          // close connection and session
          requestor.close(); // also close the session
          authConnection.close();
        } 
        catch (Exception e)
        {
          throw new EJBException("user authentication failed: " +e+"<br>");
        }
        if (userId == -1)
        {
           html.append(" You don't have an account on RUBiS!<br>You have to register first.<br>");
           return html.toString();
        }
      }
    // Try to find the user corresponding to the 'to' ID
    String toName=null, itemName=null;
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      stmt.setInt(1, toId);
      rs = stmt.executeQuery();
      if (rs.first())
        toName = rs.getString("nickname");
      stmt.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to execute Query for user name: " +e);
    }

    try
    {
      stmt = conn.prepareStatement("SELECT name FROM items WHERE id=?");
      stmt.setInt(1, itemId);
      rs = stmt.executeQuery();
      if (rs.first())
        itemName = rs.getString("name");
      if (stmt != null) stmt.close();
      if (conn != null) conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to execute Query for item name: " +e);
    }

    try
    {
      html.append("<center><h2>Give feedback about your experience with "+toName+"</h2><br>\n");
      html.append("<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreComment\" method=POST>\n");
      html.append("<input type=hidden name=to value="+toId+">\n");
      html.append("<input type=hidden name=from value="+userId+">\n");
      html.append("<input type=hidden name=itemId value="+itemId+">\n");
      html.append("<center><table>\n");
      html.append("<tr><td><b>From</b><td>"+username+"\n");
      html.append("<tr><td><b>To</b><td>"+toName+"\n");
      html.append("<tr><td><b>About item</b><td>"+itemName+"\n");
    }
    catch (Exception e)
    {
      throw new RemoteException("Cannot build comment form: " +e);
    }
 
    return html.toString();
  }
                   



  // ======================== EJB related methods ============================

  /** 
   * Set the associated context. The container call this method
   * after the instance creation. 
   * The enterprise Bean instance should store the reference to the context
   * object in an instance variable. 
   * This method is called with no transaction context.
   *
   * @param MessageDrivenContext A MessageDrivenContext interface for the instance.
   * @throws EJBException Thrown by the method to indicate a failure caused by
   * a system-level error.
   */
  public void setMessageDrivenContext(MessageDrivenContext ctx)
  {
    messageDrivenContext = ctx;
    if (dataSource == null)
    {
      // Finds DataSource from JNDI
      try
      {
        initialContext = new InitialContext(); 
        dataSource = (DataSource)initialContext.lookup("java:comp/env/jdbc/rubis");
      }
      catch (Exception e) 
      {
        throw new EJBException("Cannot get JNDI InitialContext");
      }
    }
  }

  /**
   * The Message driven  bean must define an ejbCreate methods with no args.
   *
   */
  public void ejbCreate() 
  {

  }
 
  /**
   * A container invokes this method before it ends the life of the message-driven object. 
   * This happens when a container decides to terminate the message-driven object. 
   *
   * This method is called with no transaction context. 
   *
   * @throws EJBException Thrown by the method to indicate a failure caused by
   * a system-level error.
   */
  public void ejbRemove() {}
 
}
