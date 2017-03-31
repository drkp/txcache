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
import java.io.Serializable;

/**
 * This is a stateless session bean used to build the html form to put a bid.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_PutBid implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_PutBid()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      int itemId = request.getInt("itemId");
      String username = request.getString("username");
      String password = request.getString("password");  

        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // get the post comment form
      String html = getBiddingForm(itemId, username, password);

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
      throw new EJBException("Message traitment failed for MDB_PutBid: " +e);
    }
  }

  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBiddingForm(int itemId, String username, String password) throws RemoteException 
  {
    int userId = -1;
    String html = "";

    // Authenticate the user who want to bid
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
           html = "You don't have an account on RUBiS!<br>You have to register first.<br>";
           return html;
        }
      }

        TopicConnection itemConnection;
        TopicSession itemSession;
        Topic itemTopic;
        try 
        {
          // create a connection
          itemConnection = connectionFactory.createTopicConnection();
          // lookup the destination
          itemTopic = (Topic) initialContext.lookup(BeanConfig.PrefixTopicName+"topicViewItem");
          // create a session
          itemSession  = itemConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
        } 
        catch (Exception e)
        {
          throw new EJBException("Cannot connect to message bean MDB_ViewItem : " +e+"<br>");
        }
        try 
        {
          // create a requestor to receive the reply
          TopicRequestor requestor = new TopicRequestor(itemSession, itemTopic);
          // create a message
          MapMessage m = itemSession.createMapMessage();
          // set parameters
          m.setInt("itemId", itemId);
          m.setInt("userId", userId);
          m.setJMSCorrelationID("viewItem");
          // send the message and receive the reply
          itemConnection.start(); // allows message to be delivered (default is connection stopped)
          TextMessage itemReply = (TextMessage)requestor.request(m);
          itemConnection.stop();
          // read the reply
          html = itemReply.getText();
          // close connection and session
          requestor.close(); // also close the session
          itemConnection.close();
        } 
        catch (Exception e)
        {
          throw new EJBException("Exception getting the item information: " +e+"<br>");
        }
        return html;
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
