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
 * This is a stateless session bean used get the bid history of an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_ViewBidHistory implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_ViewBidHistory()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      int itemId = request.getInt("itemId");
        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);
      // get the bids history
      String html = getBidHistory(itemId);
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
      throw new EJBException("Message traitment failed for MDB_ViewBidHistory: " +e);
    }
  }


  /**
   * Get the list of bids related to a specific item.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBidHistory(int itemId) throws RemoteException 
  {
    StringBuffer html = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;
    String date = null, bidderName = null, itemName = null;
    float bid = 0;
    int userId = -1;

    // get the item
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT name FROM items WHERE id=?");
      stmt.setInt(1, itemId);
      rs = stmt.executeQuery();
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
      throw new RemoteException("Failed to execute Query for item in items table: " +e);
    }
    try 
    {
      if (!rs.first())
      {
        stmt.close();
        stmt = conn.prepareStatement("SELECT name FROM old_items WHERE id=?");
        stmt.setInt(1, itemId);
        rs = stmt.executeQuery();

      }
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
      throw new RemoteException("Failed to execute Query for item in old_items table: " +e);
    }
    try 
    {
      if ((rs == null) || (!rs.first())) // This item does not exist
      {
        stmt.close();
        conn.close();
        return "";
      }
      else
      {
        itemName = rs.getString("name");
        html = new StringBuffer("<center><h3>Bid History for "+itemName+"<br></h3></center>");
      }
      stmt.close();
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
     throw new RemoteException("This item does not exist (got exception: " +e+")<br>");
    }
    // Get the list of the user's last bids
    try 
    {
      stmt = conn.prepareStatement("SELECT * FROM bids WHERE item_id=? ORDER BY date DESC");
      stmt.setInt(1, itemId);
      rs = stmt.executeQuery();
      if (!rs.first())
      {
        stmt.close();
        conn.close();
        return html.append("<h3>There is no bid corresponding to this item.</h3><br>").toString();
      }
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
      throw new RemoteException("Exception getting bids list: " +e+"<br>");
    }
    PreparedStatement userStmt = null;
    try
    {	
      html.append(printBidHistoryHeader());
      userStmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      ResultSet urs = null;
      do 
      {
        // Get the bids
        date = rs.getString("date");
        bid = rs.getFloat("bid");
        userId = rs.getInt("user_id");

        userStmt.setInt(1, userId);
        urs = userStmt.executeQuery();
        if (urs.first())
          bidderName = urs.getString("nickname");

        html.append(printBidHistory(userId, bidderName, bid, date));
      }
      while(rs.next());
      html.append(printBidHistoryFooter());
      userStmt.close();
      stmt.close();
      conn.close();
    }
    catch (SQLException e)
    {
      try
      {
        if (userStmt != null) userStmt.close();
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Exception getting bid: " +e+"<br>");
    }
    return html.toString();
  }

  /**
   * Display bid history information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printBidHistory(int userId, String bidderName, float bid, String date) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+userId+
      "\">"+bidderName+"<TD>"+bid+"<TD>"+date+"\n";
  }

  /** 
   * Bids list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */                   
  public String printBidHistoryHeader()
  {
    return "<TABLE border=\"1\" summary=\"List of bids\">\n<THEAD>\n"+
      "<TR><TH>User ID<TH>Bid amount<TH>Date of bid\n<TBODY>\n";
  }  

  /** 
   * Bids list footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printBidHistoryFooter()
  {
    return "</TABLE>\n";
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
