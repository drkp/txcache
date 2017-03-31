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
import javax.transaction.UserTransaction;

/**
 * This is a stateless session bean used to register a new user.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_RegisterUser implements MessageDrivenBean, MessageListener 
{
  //  private UserTransaction utx = null;

  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_RegisterUser()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      String firstname = request.getString("firstname");
      String lastname = request.getString("lastname");
      String nickname = request.getString("nickname");
      String email = request.getString("email");
      String password = request.getString("password");
      String region = request.getString("region");
        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // add a new user in the database
      String html = createUser(firstname, lastname, nickname, email, password, region);

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
      throw new EJBException("Message traitment failed for MDB_RegisterUser: " +e);
    }
  }

  /**
   * Create a new user.
   *
   * @param firstname user's first name
   * @param lastname user's last name
   * @param nickname user's nick name
   * @param email user's email
   * @param password user's password
   * @param regionName name of the region where the user live
   * @return a string in html format
   * @since 1.1
   */
  public String createUser(String firstname, String lastname, String nickname, String email, String password, String regionName) throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    int regionId           = -1;
    int userId             = -1;
    int rating             = 0;
    float balance          = 0;
    String creationDate    = null;

    //    utx = messageDrivenContext.getUserTransaction(); //bean managed transaction
    try
    {
      //     utx.begin();
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT id FROM regions WHERE name=?");
      stmt.setString(1, regionName);
      rs = stmt.executeQuery();
      if (rs.first())
      {
        regionId = rs.getInt("id");
      }
      stmt.close();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException(" Region "+regionName+" does not exist in the database!<br>(got exception: " +e+")<br>\n");
    }
    // Try to create a new user
    try
    {
      stmt = conn.prepareStatement("SELECT nickname FROM users WHERE nickname=?");
      stmt.setString(1, nickname);
      rs = stmt.executeQuery();
      stmt.close();
      conn.close();
      if (rs.first())
      {
        html.append("The nickname you have choosen is already taken by someone else. Please choose a new nickname.<br>");
        return html.toString();
      }
    }
    catch (Exception fe)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Failed to execute Query to check the nickname: " +fe);
    }
    try
    {
      try
      {
        creationDate = TimeManagement.currentDateToString();
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("INSERT INTO users VALUES (NULL, \""+firstname+
                                     "\", \""+lastname+"\", \""+nickname+"\", \""+
                                     password+"\", \""+email+"\", 0, 0,\""+creationDate+"\", "+ 
                                     regionId+")");
        stmt.executeUpdate();
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("RUBiS internal error: User registration failed (got exception: " +e+")<br>");
      }
      try
      {
        stmt = conn.prepareStatement("SELECT id, rating, balance FROM users WHERE nickname=?");
        stmt.setString(1, nickname);
        ResultSet urs = stmt.executeQuery();
        if (urs.first())
        {
          userId = urs.getInt("id");
          rating = urs.getInt("rating");
          balance = urs.getFloat("balance");
        }
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to execute Query for user: " +e);
      }
      if (conn != null) conn.close();

      html.append("User id       :"+userId+"<br>\n");
      html.append("Creation date :"+creationDate+"<br>\n");
      html.append("Rating        :"+rating+"<br>\n");
      html.append("Balance       :"+balance+"<br>\n");

      //      utx.commit();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      //      try
      //     {
      //        utx.rollback();
        throw new RemoteException("User registration failed (got exception: " +e+")<br>");
       //     }
      //      catch (Exception se) 
      //      {
      //        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
       //     }
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
