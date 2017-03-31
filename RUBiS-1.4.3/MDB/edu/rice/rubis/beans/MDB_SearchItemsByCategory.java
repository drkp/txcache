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
 * This is a message driven bean used to get the list of items
 * that belong to a specific category. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_SearchItemsByCategory implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;

  public MDB_SearchItemsByCategory()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      int id = request.getInt("categoryId");
      Integer categoryId = new Integer(id);
      int page = request.getInt("page");
      int nbOfItems = request.getInt("nbItems");

        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // get the list of categories
      String html = getItems(categoryId, page, nbOfItems);

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
      throw new EJBException("Message traitment failed for MDB_SearchItemsByCategory: " +e);
    }
  }

  /**
   * Get the items in a specific category.
   *
   * @return a string that is the list of items in html format
   * @since 1.1
   */
  public String getItems(Integer categoryId, int page, int nbOfItems)
  {
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null; 
    String itemName, endDate;
    int itemId;
    float maxBid, initialPrice;
    int nbOfBids=0;
    StringBuffer html = new StringBuffer(); 

    // get the list of items
    try
    {
      conn = dataSource.getConnection();

      stmt = conn.prepareStatement("SELECT items.name, items.id, items.end_date, items.max_bid, items.nb_of_bids, items.initial_price FROM items WHERE items.category=? AND end_date>=NOW() LIMIT ?,?");
      stmt.setInt(1, categoryId.intValue());
      stmt.setInt(2, page*nbOfItems);
      stmt.setInt(3, nbOfItems);
      rs = stmt.executeQuery();

    }
    catch (SQLException e)
    {

      throw new EJBException("Failed to get the items: " +e);
    }
    try 
    {
      while (rs.next())
      {
        itemName = rs.getString("name");
        itemId = rs.getInt("id");
        endDate = rs.getString("end_date");
        maxBid = rs.getFloat("max_bid");
        nbOfBids = rs.getInt("nb_of_bids");
        initialPrice = rs.getFloat("initial_price");
        if (maxBid <initialPrice)
          maxBid = initialPrice;
        html.append(printItem(itemName, itemId, maxBid, nbOfBids, endDate));
      }
      stmt.close();
      conn.close();
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
      throw new EJBException("Cannot get items list: " +e);
    }
    return html.toString();
  }


  /**
   * Display item information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printItem(String name, int id, float maxBid, int nbOfBids, String endDate)
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+
      "<TD>"+maxBid+
      "<TD>"+nbOfBids+
      "<TD>"+endDate+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutBidAuth?itemId="+id+"\"><IMG SRC=\""+BeanConfig.context+"/bid_now.jpg\" height=22 width=90></a>\n";
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
