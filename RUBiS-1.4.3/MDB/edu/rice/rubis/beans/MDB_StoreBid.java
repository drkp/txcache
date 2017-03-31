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
 * This is a stateless session bean used to create e new bid for an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_StoreBid implements MessageDrivenBean, MessageListener 
{

  //  private UserTransaction utx = null;

  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private Context initialContext = null;


  public MDB_StoreBid()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      int userId = request.getInt("userId");
      int itemId = request.getInt("itemId");
      float bid  = request.getFloat("bid");
      float maxBid  = request.getFloat("maxBid");
      int qty = request.getInt("quantity");

       // add the new comment in the database
      createBid(userId, itemId, bid, maxBid, qty);
    }
    catch (Exception e)
    {
      throw new EJBException("Message traitment failed for MDB_StoreBid: " +e);
    }
  }
  

  /**
   * Create a new bid and update the number of bids in the item.
   *
   * @param userId id of the user who is bidding
   * @param itemId id of the item related to the bid
   * @param bid value of the bid
   * @param maxBid maximum bid
   * @param qty quantity of items
   * @since 1.1
   */
  public void createBid(int userId, int itemId, float bid, float maxBid, int qty) throws RemoteException
  {
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

    //    utx = messageDrivenContext.getUserTransaction();
    try
    {
      //      utx.begin();
      try 
      {
        // create new bid
        conn = dataSource.getConnection();
        String now = TimeManagement.currentDateToString();
        stmt = conn.prepareStatement("INSERT INTO bids VALUES (NULL, \""+userId+
				   "\", \""+itemId+"\", \""+qty+"\", \""+
				   bid+"\", \""+maxBid+"\", \""+now+"\")");
        stmt.executeUpdate();
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Error while storing the bid (got exception: " +e+")<br>");
      }
      // update the number of bids and the current price for the item
      PreparedStatement update = null;
      try
      {
        stmt = conn.prepareStatement("SELECT nb_of_bids, max_bid FROM items WHERE id=?");
        stmt.setInt(1, itemId);
        rs = stmt.executeQuery();
        if (rs.first())
        {
          int nbOfBids = rs.getInt("nb_of_bids");
          nbOfBids++;
          float oldMaxBid = rs.getFloat("max_bid");
          if (bid > oldMaxBid)
          {
            oldMaxBid = bid;
            update = conn.prepareStatement("UPDATE items SET max_bid=?,nb_of_bids=? WHERE id=?"); // AND replaced by ","
            update.setFloat(1, oldMaxBid);
            update.setInt(2, nbOfBids);
            update.setInt(3, itemId);
            update.executeUpdate();
          }
          else
          {
            update = conn.prepareStatement("UPDATE items SET nb_of_bids=? WHERE id=?");
            update.setInt(1, nbOfBids);
            update.setInt(2, itemId);
            update.executeUpdate();
          }
          update.close();
        }
        stmt.close();
      }
      catch (Exception ex) 
      {
        try { update.close(); } catch (Exception ignore) {}
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to update nb of bids and max bid: " + ex);
      }
      if (conn != null) conn.close();
      //      utx.commit();
    }
    catch (Exception e)
    {
      try { conn.close(); } catch (Exception ignore) {}
      //      try
      //      {
      //        utx.rollback();
        throw new RemoteException("Failed to create a new bid (got exception: " +e+")<br>");
       //     }
      //      catch (Exception se) 
       //     {
       //       throw new RemoteException("Transaction rollback failed: " + e +"<br>");
       //     }
    }
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
