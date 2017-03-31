package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.CreateException;
import javax.ejb.RemoveException;
import javax.ejb.EJBException;
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

public class SB_StoreBidBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;

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

    utx = sessionContext.getUserTransaction();
    try
    {
      utx.begin();
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
      // update the number of bids and the max bid for the item
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
            update = conn.prepareStatement("UPDATE items SET max_bid=?,nb_of_bids=? WHERE id=?");
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
          stmt.close();
          update.close();
        }
      }
      catch (Exception ex) 
      {
        try { update.close(); } catch (Exception ignore) {}
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to update nb of bids and max bid: " + ex);
      }
      if (conn != null) conn.close();
      utx.commit();
    }
    catch (Exception e)
    {
      try { conn.close(); } catch (Exception ignore) {}
      try
      {
        utx.rollback();
        throw new RemoteException("Failed to create a new bid (got exception: " +e+")<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
    }
  }



  // ======================== EJB related methods ============================

  /**
   * This method is empty for a stateless session bean
   */
  public void ejbCreate() throws CreateException, RemoteException
  {
  }

  /** This method is empty for a stateless session bean */
  public void ejbActivate() throws RemoteException {}
  /** This method is empty for a stateless session bean */
  public void ejbPassivate() throws RemoteException {}
  /** This method is empty for a stateless session bean */
  public void ejbRemove() throws RemoteException {}


  /** 
   * Sets the associated session context. The container calls this method 
   * after the instance creation. This method is called with no transaction context. 
   * We also retrieve the Home interfaces of all RUBiS's beans.
   *
   * @param sessionContext - A SessionContext interface for the instance. 
   * @exception RemoteException - Thrown if the instance could not perform the function 
   *            requested by the container because of a system-level error. 
   */
  public void setSessionContext(SessionContext sessionContext) throws RemoteException
  {
    this.sessionContext = sessionContext;
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
        throw new RemoteException("Cannot get JNDI InitialContext");
      }
    }
  }

}
