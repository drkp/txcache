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
 * This is a stateless session bean used when a user buy an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_StoreBuyNow implements MessageDrivenBean, MessageListener
{
      //  private UserTransaction utx = null;

  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private Context initialContext = null;


  public MDB_StoreBuyNow()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      int itemId = request.getInt("itemId");
      int userId = request.getInt("userId");
      int qty = request.getInt("quantity");

       // add the new comment in the database
      createBuyNow(itemId, userId, qty);
    }
    catch (Exception e)
    {
      throw new EJBException("Message traitment failed for MDB_StoreBuyNow: " +e);
    }
  }

  /**
   * Create a buyNow and update the item.
   *
   * @param itemId id of the item related to the comment
   * @param userId id of the buyer
   * @param qty quantity of items
   * @since 1.1
   */
  public void createBuyNow(int itemId, int userId, int qty) throws RemoteException
  {
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

      //    utx = messageDrivenContext.getUserTransaction();
    try
    {
       //     utx.begin();
      // Try to find the Item corresponding to the Item ID
      String now = TimeManagement.currentDateToString();
      int quantity;
      try 
      {
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("SELECT quantity, end_date FROM items WHERE id=?");
        stmt.setInt(1, itemId);
        rs = stmt.executeQuery();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to execute Query for the item: " +e+"<br>");
      }
      PreparedStatement update = null;
      try
      {  
        if (rs.first())
        {
          quantity = rs.getInt("quantity");
          quantity = quantity -qty;
           if (quantity == 0)
          {
            update = conn.prepareStatement("UPDATE items SET end_date=?,quantity=? WHERE id=?");
            update.setString(1, now);
            update.setInt(2, quantity);
            update.setInt(3, itemId);
            update.executeUpdate();
          }
          else
          {
            update = conn.prepareStatement("UPDATE items SET quantity=? WHERE id=?");
            update.setInt(1, quantity);
            update.setInt(2, itemId);
            update.executeUpdate();
          }
          update.close();
        }
        stmt.close();
      }
      catch (Exception e)
      {
        try { update.close(); } catch (Exception ignore) {}
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to update item's quantity: " +e+"<br>");
      }
      try 
      {
        stmt = conn.prepareStatement("INSERT INTO buy_now VALUES (NULL, \""+userId+
                                     "\", \""+itemId+"\", \""+qty+"\", \""+now+"\")");
        stmt.executeUpdate();
      }
      catch (Exception e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to create buy_now item: " +e+"<br>");
      }
      if (stmt != null) stmt.close();
      if (conn != null) conn.close();
       //     utx.commit();

    } 
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
       //     try
      //      {
       //       utx.rollback();
        throw new RemoteException("Cannot insert the item into buy_now items table: " +e+"<br>");
       //     }
       //     catch (Exception se) 
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
