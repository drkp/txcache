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
 * This is a stateless session bean used to create e new comment for a user.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_StoreComment implements MessageDrivenBean, MessageListener 
{
  //  private UserTransaction utx = null;

  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private Context initialContext = null;


  public MDB_StoreComment()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      int fromId = request.getInt("fromId");
      int toId = request.getInt("toId");
      int itemId = request.getInt("itemId");
      int rating = request.getInt("rating");
      String comment = request.getString("comment");

       // add the new comment in the database
      createComment(fromId, toId, itemId, rating, comment);
    }
    catch (Exception e)
    {
      throw new EJBException("Message traitment failed for MDB_StoreComment: " +e);
    }
  }

  /**
   * Create a new comment and update the rating of the user.
   *
   * @param fromId id of the user posting the comment
   * @param toId id of the user who is the subject of the comment
   * @param itemId id of the item related to the comment
   * @param rating value of the rating for the user
   * @param comment text of the comment
   * @since 1.1
   */
  public void createComment(int fromId, int toId, int itemId, int rating, String comment) throws RemoteException
  {
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

      //    utx = messageDrivenContext.getUserTransaction(); //bean managed transaction
    try
    {
       //     utx.begin();
      try 
      {
        // create new comment
        String now = TimeManagement.currentDateToString();
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("INSERT INTO comments VALUES (NULL, \""+
                                     fromId+
                                     "\", \""+toId+"\", \""+itemId+
                                     "\", \""+ rating+"\", \""+now+"\",\""+comment+"\")");

        stmt.executeUpdate();
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
       throw new RemoteException("Error while storing the comment (got exception: " +e+")<br>");
      }
      // Try to find the user corresponding to the 'to' ID
      try
      {
        stmt = conn.prepareStatement("SELECT rating FROM users WHERE id=?");
        stmt.setInt(1, toId);
        rs = stmt.executeQuery();
        if (rs.first())
        {
          int userRating = rs.getInt("rating");
          userRating = userRating + rating;
 
          PreparedStatement userStmt = conn.prepareStatement("UPDATE users SET rating=? WHERE id=?");
          userStmt.setInt(1, userRating);
          userStmt.setInt(2, toId);
          userStmt.executeUpdate();
          userStmt.close();
        }
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Error while updating user's rating (got exception: " +e+")<br>");
      }
      if (conn != null) conn.close();
       //     utx.commit();
    }
    catch (Exception e)
    {
      try { conn.close(); } catch (Exception ignore) {}
      //      try
       //     {
       //       utx.rollback();
        throw new RemoteException("Error while storing the comment (got exception: " +e+")<br>");
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
