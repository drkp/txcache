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
import java.net.URLEncoder;

/**
 * This is a message driven bean used to get the list of 
 * categories from database and return the information to the BrowseCategories servlet. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_BrowseCategories implements MessageDrivenBean, MessageListener
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_BrowseCategories()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      String region = request.getString("region");
      String nickname = request.getString("nickname");
      String password = request.getString("password");


        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);
      // get the list of categories
      String html = getCategories(region, nickname, password);
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
      throw new EJBException("Message traitment failed for MDB_BrowseCategories: " +e);
    }
  }


  /**
   * Get all the categories from the database.
   *
   * @return a string that is the list of categories in html format
   * @since 1.1
   */
  /** List all the categories in the database */
  public String getCategories(String regionName, String username, String password) throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    String categoryName;
    int categoryId;
    int regionId = -1;
    int userId = -1;

    if (regionName != null && !regionName.equals(""))
    {
      // get the region ID
      try 
      {
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("SELECT id FROM regions WHERE name=?");
        stmt.setString(1, regionName);
        rs = stmt.executeQuery();
        stmt.close();
      }
      catch (SQLException e)
      {
        try
        {
          if (stmt != null) stmt.close();
          if (conn != null) conn.close();
        }
        catch (Exception ignore)
        {
        }
        throw new RemoteException("Failed to get region Id " +e);
      }
      try
      {
        if (rs.first())
        {
          regionId = rs.getInt("id");
        }
      }
      catch (Exception e)
      {
        try
        {
          if (conn != null) conn.close();
        }
        catch (Exception ignore)
        {
        }
        throw new EJBException(" Region "+regionName+" does not exist in the database!<br>(got exception: " +e+")");
      }
    }
    else
    {
      // Authenticate the user who wants to sell items
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
    }
    try 
    {
      if (conn == null)
        conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT name, id FROM categories");
      rs = stmt.executeQuery();
    }
    catch (SQLException e)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to get categories list " +e);
    }
    try 
    {
      if (!rs.first())
        html.append("<h2>Sorry, but there is no category available at this time. Database table is empty</h2><br>");
      else
      {
        do
        {
          categoryName = rs.getString("name");
          categoryId = rs.getInt("id");
          if (regionId != -1)
          {
            html.append(printCategoryByRegion(categoryName, categoryId, regionId));
          }
          else
          {
            if (userId != -1)
              html.append(printCategoryToSellItem(categoryName, categoryId, userId));
            else
              html.append(printCategory(categoryName, categoryId));
          }
        }
        while (rs.next());
      }
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
      throw new EJBException("Exception getting category list: " + e);
    }
    return html.toString();
  }

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printCategory(String name, int id)
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+id+
                  "&categoryName="+URLEncoder.encode(name)+"\">"+name+"</a><br>\n";
  }

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printCategoryByRegion(String name, int id, int regionId)
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="+id+
      "&categoryName="+URLEncoder.encode(name)+"&region="+regionId+"\">"+name+"</a><br>\n";
  }


  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printCategoryToSellItem(String name, int id, int userId)
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.SellItemForm?category="+id+"&user="+userId+"\">"+name+"</a><br>\n";
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
