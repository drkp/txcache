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
 * This is a stateless session bean used to register a new item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_RegisterItem implements MessageDrivenBean, MessageListener 
{
  private UserTransaction utx = null;

  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_RegisterItem()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      String name = request.getString("name");
      String description = request.getString("description");
      float initialPrice = request.getFloat("initialPrice");
      int quantity = request.getInt("quantity");
      float reservePrice = request.getFloat("reservePrice");
      float buyNow = request.getFloat("buyNow");
      String startDate = request.getString("startDate");
      String endDate = request.getString("endDate");
      int userId = request.getInt("userId");
      int categoryId = request.getInt("categoryId");

        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // add a new item in the database
      String html = createItem(name, description, initialPrice, quantity, reservePrice, buyNow, startDate, endDate, userId, categoryId);

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
      throw new EJBException("Message traitment failed for MDB_RegisterItem: " +e);
    }
  }

  /**
   * Create a new item.
   *
   * @param name name of the item
   * @param description item's description
   * @param initialPrice item's initial price
   * @param quantity number of items
   * @param reservePrice item's reserve price
   * @param buyNow item's price to buy it now
   * @param startDate auction's start date
   * @param endDate auction's end date
   * @param userId seller id
   * @param catagoryId category id
   * @return a string in html format
   * @since 1.1
   */
  public String createItem(String name, String description, float initialPrice, int quantity, float reservePrice, float buyNow, String startDate, String endDate, int userId, int categoryId) throws RemoteException
  {
    String html;
    int itemId = -1;
    Connection        conn = null;
    PreparedStatement stmt = null;

    utx = messageDrivenContext.getUserTransaction();
    // Try to create a new item
    try 
    {
      utx.begin();
      try 
      {
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("INSERT INTO items VALUES (NULL, \""+name+
                                     "\", \""+description+"\", \""+initialPrice+"\", \""+
                                     quantity+"\", \""+reservePrice+"\", \""+buyNow+
                                     "\", 0, 0, \""+startDate+"\", \""+endDate+"\", \""+userId+
                                     "\", "+ categoryId+")");
        stmt.executeUpdate();
        stmt.close();
      }
      catch (Exception e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to create the item: " +e);
      }
      // To test if the item was correctly added in the database
      try
      {
        stmt = conn.prepareStatement("SELECT id FROM items WHERE name=?");
        stmt.setString(1, name);
        ResultSet irs = stmt.executeQuery();
        if (!irs.first())
        {
          try { stmt.close(); } catch (Exception ignore) {}
          try { conn.close(); } catch (Exception ignore) {}
          throw new RemoteException("This item does not exist in the database.");
        }
        itemId = irs.getInt("id");
        
        html = "<TR><TD>Item id<TD>"+itemId+"\n";
      }
      catch (Exception e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to retrieve the item id: " +e);
      }
      if (stmt != null) stmt.close();
      if (conn != null) conn.close();
      utx.commit();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      try
      {
        utx.rollback();
        throw new RemoteException("Item registration failed (got exception: " +e+")<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
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
