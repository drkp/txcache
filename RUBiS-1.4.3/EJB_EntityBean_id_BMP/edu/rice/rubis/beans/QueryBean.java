package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * MySQL version of Query Bean:
 * QueryBean is a stateless session bean used to perform requests
 * on the RUBiS database as described in rubis.sql. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class QueryBean implements SessionBean
{
  protected SessionContext sessionContext;
  protected DataSource dataSource = null;

  /** 
   * Get all the items that match a specific category and that are still
   * to sell (auction end date is not passed). You must select the starting
   * row and number of rows to fetch from the database to get only a limited 
   *number of items.
   * For example, returns 25 Books.
   *
   * @param categoryId id of the category you are looking for
   * @param regionId id of the region you are looking for
   * @param startingRow row where result starts (0 if beginning)
   * @param nbOfRows number of rows to get
   *
   * @return Vector of items primary keys
   * @since 1.1
   */
  public Vector getCurrentItemsInCategory(
    Integer categoryId,
    int startingRow,
    int nbOfRows)
    throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    Vector v = new Vector(nbOfRows);

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT items.id FROM items WHERE items.category=? AND end_date>=NOW() ORDER BY items.end_date ASC LIMIT ?,?");
      stmt.setInt(1, categoryId.intValue());
      stmt.setInt(2, startingRow); // MySQL version
      stmt.setInt(3, nbOfRows); // MySQL version
      ResultSet rs = stmt.executeQuery();
      // Build the vector of primary keys
      while (rs.next())
      {
        ItemPK iPK = new ItemPK(new Integer(rs.getInt("id")));
        v.addElement((Object) iPK);
      };
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return v;
  }

  /** 
   * Get all the items that match a specific category and region and
   * that are still to sell (auction end date is not passed). You must
   * select the starting row and number of rows to fetch from the database
   * to get only a limited number of items.
   * For example, returns 25 Books to sell in Houston.
   *
   * @param categoryId id of the category you are looking for
   * @param regionId id of the region you are looking for
   * @param startingRow row where result starts (0 if beginning)
   * @param nbOfRows number of rows to get
   *
   * @return Vector of items primary keys
   * @since 1.1
   */
  public Vector getCurrentItemsInCategoryAndRegion(
    Integer categoryId,
    Integer regionId,
    int startingRow,
    int nbOfRows)
    throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    Vector v = new Vector(nbOfRows);

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT items.id FROM items,users WHERE items.category=? AND items.seller=users.id AND users.region=? AND end_date>=NOW() ORDER BY items.end_date ASC LIMIT ?,?");
      stmt.setInt(1, categoryId.intValue());
      stmt.setInt(2, regionId.intValue());
      stmt.setInt(3, startingRow); // MySQL version
      stmt.setInt(4, nbOfRows); // MySQL version
      ResultSet rs = stmt.executeQuery();

      // Build the vector of primary keys
      while (rs.next())
      {
        ItemPK iPK = new ItemPK(new Integer(rs.getInt("id")));
        v.addElement((Object) iPK);
      };
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return v;
  }

  /**
   * Get the maximum bid (winning bid) for an item.
   *
   * @param itemId item id
   *
   * @return maximum bid or 0 if no bid
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getItemMaxBid(Integer itemId) throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    float maxBid = 0;

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT MAX(bid) AS bid FROM bids WHERE item_id=?");
      stmt.setInt(1, itemId.intValue());
      ResultSet rs = stmt.executeQuery();

      // Get the max
      if (rs.next())
        maxBid = rs.getFloat("bid");
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return maxBid;
  }

  /**
   * Get the first <i>maxToCollect</i> bids for an item sorted from the
   * maximum to the minimum.
   *
   * @param maxToCollect number of bids to collect
   * @param itemId item id
   *
   * @return Vector of bids primary keys (can be less than maxToCollect)
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Vector getItemQtyMaxBid(int maxToCollect, Integer itemId)
    throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    Vector v = new Vector();

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT id FROM bids WHERE item_id=? ORDER BY bid DESC LIMIT ?");
      stmt.setInt(1, itemId.intValue());
      stmt.setInt(2, maxToCollect);
      ResultSet rs = stmt.executeQuery();

      // Build the vector of primary keys
      while (rs.next())
      {
        BidPK bPK = new BidPK(new Integer(rs.getInt("id")));
        v.addElement((Object) bPK);
      };
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return v;
  }

  /**
   * Get the number of bids for an item.
   *
   * @param itemId item id
   *
   * @return number of bids or 0 if no bid
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public int getItemNbOfBids(Integer itemId) throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    int nbOfBid = 0;

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT COUNT(*) AS bid FROM bids WHERE item_id=?");
      stmt.setInt(1, itemId.intValue());
      ResultSet rs = stmt.executeQuery();

      // Get the max
      if (rs.next())
        nbOfBid = rs.getInt("bid");
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return nbOfBid;
  }

  /**
   * Get the bid history for an item sorted from the last bid to the
   * first bid (oldest one).
   *
   * @param itemId item id
   *
   * @return Vector of bids primary keys or null if no bids
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Vector getItemBidHistory(Integer itemId) throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    Vector v = new Vector();

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT id FROM bids WHERE item_id=? ORDER BY date DESC");
      stmt.setInt(1, itemId.intValue());
      ResultSet rs = stmt.executeQuery();

      // Build the vector of primary keys
      while (rs.next())
      {
        BidPK bPK = new BidPK(new Integer(rs.getInt("id")));
        v.addElement((Object) bPK);
      };
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return v;
  }

  /**
   * Get all the items the user won in the last 30 days.
   *
   * @param userId user id
   *
   * @return Vector of items primary keys (can be less than maxToCollect)
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Vector getUserWonItems(Integer userId) throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    Vector v = new Vector();

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT DISTINCT MAX(bid),item_id FROM bids, items WHERE bids.user_id=? AND bids.item_id=items.id AND TO_DAYS(NOW()) - TO_DAYS(items.end_date) < 30 ORDER BY item_id");
      stmt.setInt(1, userId.intValue());
      ResultSet rs = stmt.executeQuery();

      // Build the vector of primary keys
      while (rs.next())
      {
        ItemPK iPK = new ItemPK(new Integer(rs.getInt("bids.item_id")));
        v.addElement((Object) iPK);
      };
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return v;
  }

  /**
   * Get all the maximum bids for each item the user has bid on in the last 30 days.
   *
   * @param userId user id
   *
   * @return Vector of bids primary keys (can be less than maxToCollect)
   * @exception RemoteException if an error occurs
   */
  public Vector getUserBids(Integer userId) throws RemoteException
  {
    Connection conn = null;
    PreparedStatement stmt = null;
    Vector v = new Vector();

    try
    {
      conn = dataSource.getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT DISTINCT MAX(bid),bids.id FROM bids,items WHERE user_id=? AND bids.item_id=items.id AND items.end_date>=NOW() ORDER BY item_id");
      stmt.setInt(1, userId.intValue());
      ResultSet rs = stmt.executeQuery();

      // Build the vector of primary keys
      while (rs.next())
      {
        BidPK bPK = new BidPK(new Integer(rs.getInt("bids.id")));
        v.addElement((Object) bPK);
      };
    }
    catch (SQLException e)
    {
      throw new EJBException("Failed to executeQuery " + e);
    }
    finally
    {
      try
      {
        if (stmt != null)
          stmt.close(); // close statement
        if (conn != null)
          conn.close(); // release connection
      }
      catch (Exception ignore)
      {
      }
    }
    return v;
  }

  // ======================== EJB related methods ============================

  /**
   * This method is empty for a stateless session bean
   */
  public void ejbCreate() throws CreateException, RemoteException
  {
  }

  /** This method is empty for a stateless session bean */
  public void ejbActivate() throws RemoteException
  {
  }
  /** This method is empty for a stateless session bean */
  public void ejbPassivate() throws RemoteException
  {
  }
  /** This method is empty for a stateless session bean */
  public void ejbRemove() throws RemoteException
  {
  }

  /** 
   * Sets the associated session context. The container calls this method 
   * after the instance creation. This method is called with no transaction context. 
   * We also retrieve the Home interfaces of all RUBiS's beans.
   *
   * @param sessionContext - A SessionContext interface for the instance. 
   * @exception RemoteException - Thrown if the instance could not perform the function 
   *            requested by the container because of a system-level error. 
   */
  public void setSessionContext(SessionContext sessionContext)
    throws RemoteException
  {
    this.sessionContext = sessionContext;
    if (dataSource == null)
    {
      // Finds DataSource from JNDI
      Context initialContext = null;
      try
      {
        initialContext = new InitialContext();
        dataSource =
          (DataSource) initialContext.lookup("java:comp/env/jdbc/rubis");
      }
      catch (Exception e)
      {
        throw new EJBException("Cannot get JNDI InitialContext");
      }
    }
  }

}
