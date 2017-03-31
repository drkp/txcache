package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedList;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;

/**
 * BuyNowBean is an entity bean with "bean managed persistence". 
 * The state of an instance is stored into a relational database. 
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE buy_now (
 *   id       INTEGER UNSIGNED NOT NULL UNIQUE,
 *   buyer_id INTEGER UNSIGNED NOT NULL,
 *   item_id  INTEGER UNSIGNED NOT NULL,
 *   qty      INTEGER,
 *   date     DATETIME,
 *   PRIMARY KEY(id),
 *   INDEX buyer (buyer_id),
 *   INDEX item (item_id)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class BuyNowBean implements EntityBean
{
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function
  private Context initialContext;
  private DataSource datasource;

  /* Class member variables */

  public Integer id;
  public Integer buyerId;
  public Integer itemId;
  public int qty;
  public String date;

  /**
   * Get BuyNow id.
   *
   * @return BuyNow id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get the buyer id which is the primary key in the users table.
   *
   * @return user id
   * @exception RemoteException if an error occurs
   */
  public Integer getBuyerId() throws RemoteException
  {
    return buyerId;
  }

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   * @exception RemoteException if an error occurs
   */
  public Integer getItemId() throws RemoteException
  {
    return itemId;
  }

  /**
   * Get how many of this item the user has bought.
   *
   * @return quantity of items for this BuyNow.
   * @exception RemoteException if an error occurs
   */
  public int getQuantity() throws RemoteException
  {
    return qty;
  }

  /**
   * Time of the BuyNow in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return BuyNow time
   * @exception RemoteException if an error occurs
   */
  public String getDate() throws RemoteException
  {
    return date;
  }

  /**
   * Set a new buyer identifier. This id must match
   * the primary key of the users table.
   *
   * @param id buyer id
   * @exception RemoteException if an error occurs
   */
  public void setBuyerId(Integer id) throws RemoteException
  {
    buyerId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   * @exception RemoteException if an error occurs
   */
  public void setItemId(Integer id) throws RemoteException
  {
    itemId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new quantity for this BuyNow
   *
   * @param Qty quantity
   * @exception RemoteException if an error occurs
   */
  public void setQuantity(int Qty) throws RemoteException
  {
    qty = Qty;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new date for this BuyNow
   *
   * @param newDate BuyNow date
   * @exception RemoteException if an error occurs
   */
  public void setDate(String newDate) throws RemoteException
  {
    date = newDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Retrieve a connection..
   *
   * @return connection
   * @exception Exception if an error occurs
   */
  public Connection getConnection() throws Exception
  {
    try
    {
      if (datasource == null)
      {
        // Finds DataSource from JNDI
        initialContext = new InitialContext();
        datasource =
          (DataSource) initialContext.lookup("java:comp/env/jdbc/rubis");
      }
      return datasource.getConnection();
    }
    catch (Exception e)
    {
      throw new Exception("Cannot retrieve the connection.");
    }
  }

  /**
   * This method is used to retrieve a BuyNow Bean from its primary key,
   * that is to say its id.
   *
   * @param id BuyNow id (primary key)
   *
   * @return the BuyNow if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public BuyNowPK ejbFindByPrimaryKey(BuyNowPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT bid FROM buy_now WHERE id=?");
      stmt.setInt(1, id.getId().intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        return null;
      }
      rs.close();
      stmt.close();
      conn.close();
      return id;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to retrieve object buyNow: " + e);
    }
  }

  /**
   * This method is used to retrieve all BuyNow Beans related to one item.
   * You must provide the item id.
   *
   * @param id item id
   *
   * @return List of BuyNows found (eventually empty)
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public Collection ejbFindByItem(Integer id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM buy_now WHERE buyer_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BuyNowPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to get all buyNow by item: " + e);
    }
  }

  /**
   * This method is used to retrieve all BuyNow Beans belonging to
   * a specific user. You must provide the user id.
   *
   * @param id user id
   *
   * @return List of BuyNows found (eventually empty)
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public Collection ejbFindByUser(Integer id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM buy_now WHERE item_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BuyNowPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to get all buyNow by user: " + e);
    }
  }

  /**
   * This method is used to retrieve all BuyNows from the database!
   *
   * @return List of all BuyNows (eventually empty)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection ejbFindAllBuyNows() throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM buy_now");
      ResultSet rs = stmt.executeQuery();
      int pk;
      LinkedList results = new LinkedList();
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BuyNowPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to get all buyNows: " + e);
    }
  }

  /**
   * Get all the items the user bought using the buy-now option in the last 30 days.
   *
   * @param userId user id
   *
   * @return Collection of items primary keys (can be less than maxToCollect)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection ejbFindUserBuyNow(Integer userId)
    throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement(
          "SELECT id FROM buy_now WHERE buy_now.buyer_id=? AND TO_DAYS(NOW()) - TO_DAYS(buy_now.date)<=30");
      stmt.setInt(1, userId.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BuyNowPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException(
        "Failed to get items a user bought in the past 30 days: " + e);
    }
  }

  /**
   * This method is used to create a new BuyNow Bean.
   * The date is automatically set to the current date when the method is called.
   *
   * @param BuyNowUserId user id of the buyer, must match the primary key of table users
   * @param BuyNowItemId item id, must match the primary key of table items
   * @param quantity number of items the user wants to buy
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public BuyNowPK ejbCreate(
    Integer BuyNowUserId,
    Integer BuyNowItemId,
    int quantity)
    throws CreateException, RemoteException, RemoveException
  {
    // Connecting to IDManager Home interface thru JNDI
    IDManagerHome home = null;
    IDManager idManager = null;

    try
    {
      InitialContext initialContext = new InitialContext();
      home =
        (IDManagerHome) PortableRemoteObject.narrow(
          initialContext.lookup("java:comp/env/ejb/IDManager"),
          IDManagerHome.class);
    }
    catch (Exception e)
    {
      throw new EJBException("Cannot lookup IDManager: " + e);
    }
    try
    {
      IDManagerPK idPK = new IDManagerPK();
      idManager = home.findByPrimaryKey(idPK);
      id = idManager.getNextBuyNowID();
      buyerId = BuyNowUserId;
      itemId = BuyNowItemId;
      qty = quantity;
      date = TimeManagement.currentDateToString();
    }
    catch (Exception e)
    {
      throw new EJBException("Cannot create id for buyNow: " + e);
    }
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement(
          "INSERT INTO buy_now VALUES ("
            + id.intValue()
            + ", \""
            + buyerId.intValue()
            + "\", \""
            + itemId.intValue()
            + "\", \""
            + qty
            + "\", \""
            + date
            + "\")");
      stmt.executeUpdate();
      stmt.close();
      conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to create object buyNow: " + e);
    }
    return new BuyNowPK(id);
  }

  /** This method does currently nothing */
  public void ejbPostCreate(
    Integer BuyNowUserId,
    Integer BuyNowItemId,
    int quantity)
  {
  }

  /** Mandatory methods */
  public void ejbActivate() throws RemoteException
  {
  }
  public void ejbPassivate() throws RemoteException
  {
  }

  /**
   * This method delete the record from the database.
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public void ejbRemove() throws RemoteException, RemoveException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("DELETE FROM buy_now WHERE id=?");
      stmt.setInt(1, id.intValue());
      stmt.executeUpdate();
      stmt.close();
      conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to remove object buyNow: " + e);
    }

  }

  /**
   * Update the record.
   * @exception RemoteException if an error occurs
   */
  public void ejbStore() throws RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    if (isDirty)
    {
      isDirty = false;
      try
      {
        conn = getConnection();
        stmt =
          conn.prepareStatement(
            "UPDATE buy_now SET buyer_id=?, item_id=?, qty=?, date=? WHERE id=?");
        stmt.setInt(1, buyerId.intValue());
        stmt.setInt(2, itemId.intValue());
        stmt.setInt(3, qty);
        stmt.setString(4, date);
        stmt.setInt(5, id.intValue());
        stmt.executeUpdate();
        stmt.close();
        conn.close();
      }
      catch (Exception e)
      {
        try
        {
          if (stmt != null)
            stmt.close();
          if (conn != null)
            conn.close();
        }
        catch (Exception ignore)
        {
        }
        throw new EJBException("Failed to update the record for buyNow: " + e);
      }
    }
  }

  /**
   * Read the reccord from the database and update the bean.
   * @exception RemoteException if an error occurs
   */
  public void ejbLoad() throws RemoteException
  {
    isDirty = false;
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      BuyNowPK pk = (BuyNowPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT * FROM buy_now WHERE id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new EJBException("Object buyNow not found");
      }
      buyerId = new Integer(rs.getInt("buyer_id"));
      itemId = new Integer(rs.getInt("item_id"));
      qty = rs.getInt("qty");
      date = rs.getString("date");
      rs.close();
      stmt.close();
      conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new EJBException("Failed to update buyNow bean: " + e);
    }
  }

  /**
   * Sets the associated entity context. The container invokes this method 
   *  on an instance after the instance has been created. 
   * 
   * This method is called in an unspecified transaction context. 
   * 
   * @param context An EntityContext interface for the instance. The instance should 
   *              store the reference to the context in an instance variable. 
   * @exception EJBException  Thrown by the method to indicate a failure 
   *                          caused by a system-level error.
   * @exception RemoteException - This exception is defined in the method signature
   *                           to provide backward compatibility for enterprise beans
   *                           written for the EJB 1.0 specification. 
   *                           Enterprise beans written for the EJB 1.1 and 
   *                           higher specification should throw the javax.ejb.EJBException 
   *                           instead of this exception. 
   */
  public void setEntityContext(EntityContext context) throws RemoteException
  {
    entityContext = context;
  }

  /**
   * Unsets the associated entity context. The container calls this method 
   *  before removing the instance. This is the last method that the container 
   *  invokes on the instance. The Java garbage collector will eventually invoke 
   *  the finalize() method on the instance. 
   *
   * This method is called in an unspecified transaction context. 
   * 
   * @exception EJBException  Thrown by the method to indicate a failure 
   *                          caused by a system-level error.
   * @exception RemoteException - This exception is defined in the method signature
   *                           to provide backward compatibility for enterprise beans
   *                           written for the EJB 1.0 specification. 
   *                           Enterprise beans written for the EJB 1.1 and 
   *                           higher specification should throw the javax.ejb.EJBException 
   *                           instead of this exception.
   */
  public void unsetEntityContext() throws RemoteException
  {
    entityContext = null;
  }

}
