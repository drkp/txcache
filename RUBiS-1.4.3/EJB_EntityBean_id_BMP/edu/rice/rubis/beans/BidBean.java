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
 * BidBean is an entity bean with "bean managed persistence". 
 * The state of an instance is stored into a relational database. 
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE bids (
 *    id      INTEGER UNSIGNED NOT NULL UNIQUE,
 *    user_id INTEGER,
 *    item_id INTEGER,
 *    qty     INTEGER,
 *    bid     FLOAT UNSIGNED NOT NULL,
 *    max_bid FLOAT UNSIGNED NOT NULL,
 *    date    DATETIME
 *   INDEX item (item_id),
 *   INDEX user (user_id)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class BidBean implements EntityBean
{
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function
  private Context initialContext;
  private DataSource datasource;

  /* Class member variables */

  public Integer id;
  public Integer userId;
  public Integer itemId;
  public int qty;
  public float bid;
  public float maxBid;
  public String date;

  /**
   * Get bid's id.
   *
   * @return bid id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get the user id which is the primary key in the users table.
   *
   * @return user id
   * @exception RemoteException if an error occurs
   */
  public Integer getUserId() throws RemoteException
  {
    return userId;
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
   * Get how many of this item the user wants.
   *
   * @return quantity of items for this bid.
   * @exception RemoteException if an error occurs
   */
  public int getQuantity() throws RemoteException
  {
    return qty;
  }

  /**
   * Get the bid of the user.
   *
   * @return user's bid
   * @exception RemoteException if an error occurs
   */
  public float getBid() throws RemoteException
  {
    return bid;
  }

  /**
   * Get the maximum bid wanted by the user.
   *
   * @return user's maximum bid
   * @exception RemoteException if an error occurs
   */
  public float getMaxBid() throws RemoteException
  {
    return maxBid;
  }

  /**
   * Time of the Bid in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return bid time
   * @exception RemoteException if an error occurs
   */
  public String getDate() throws RemoteException
  {
    return date;
  }

  /**
   * Give the nick name of the bidder
   *
   * @return bidder's nick name
   * @exception RemoteException if an error occurs
   */
  public String getBidderNickName() throws RemoteException
  {
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    }
    catch (Exception e)
    {
      System.err.print("Cannot get initial context for JNDI: " + e);
      return null;
    }

    // Try to find the user nick name corresponding to the sellerId
    UserHome uHome;
    try
    {
      uHome =
        (UserHome) PortableRemoteObject.narrow(
          initialContext.lookup("UserHome"),
          UserHome.class);
    }
    catch (Exception e)
    {
      System.err.print("Cannot lookup User: " + e);
      return null;
    }
    try
    {
      User u = uHome.findByPrimaryKey(new UserPK(userId));
      return u.getNickName();
    }
    catch (Exception e)
    {
      System.err.print(
        "This user does not exist (got exception: " + e + ")<br>");
      return null;
    }
  }

  /**
   * Set a new user identifier. This id must match
   * the primary key of the users table.
   *
   * @param id user id
   * @exception RemoteException if an error occurs
   */
  public void setUserId(Integer id) throws RemoteException
  {
    userId = id;
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
   * Set a new quantity for this bid
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
   * Set a new bid on the item for the user.
   * <pre>
   * Warning! This method does not update the maxBid value in the items table
   * </pre>
   *
   * @param newBid a <code>float</code> value
   * @exception RemoteException if an error occurs
   */
  public void setBid(float newBid) throws RemoteException
  {
    bid = newBid;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new maximum bid on the item for the user
   *
   * @param newBid a <code>float</code> value
   * @exception RemoteException if an error occurs
   */
  public void setMaxBid(float newBid) throws RemoteException
  {
    maxBid = newBid;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new date for this bid
   *
   * @param newDate bid date
   * @exception RemoteException if an error occurs
   */
  public void setDate(String newDate) throws RemoteException
  {
    date = newDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Retieve a connection..
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
   * This method is used to retrieve a Bid Bean from its primary key,
   * that is to say its id.
   *
   * @param id Bid id (primary key)
   *
   * @return the Bid primary key if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public BidPK ejbFindByPrimaryKey(BidPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT bid FROM bids WHERE id=?");
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
      throw new EJBException("Failed to retrieve object bid: " + e);
    }
  }

  /**
   * This method is used to retrieve all Bid Beans related to one item.
   * You must provide the item id.
   *
   * @param id item id
   *
   * @return List of Bids primary keys found (eventually empty)
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
      stmt = conn.prepareStatement("SELECT id FROM bids WHERE item_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BidPK(new Integer(pk)));
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
      throw new EJBException("Failed to get all bids by item: " + e);
    }
  }

  /**
   * This method is used to retrieve all Bid Beans belonging to
   * a specific user. You must provide the user id.
   *
   * @param id user id
   *
   * @return List of Bids primary keys found (eventually empty)
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
      stmt = conn.prepareStatement("SELECT id FROM bids WHERE user_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BidPK(new Integer(pk)));
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
      throw new EJBException("Failed to get all bids by user: " + e);
    }
  }

  /**
   * This method is used to retrieve all bids from the database!
   *
   * @return List of all bids primary keys (eventually empty)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection ejbFindAllBids() throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM bids");
      ResultSet rs = stmt.executeQuery();
      int pk;
      LinkedList results = new LinkedList();
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new BidPK(new Integer(pk)));
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
      throw new EJBException("Failed to get all bids: " + e);
    }
  }

  /**
   * This method is used to create a new Bid Bean.
   * The date is automatically set to the current date when the method is called.
   *
   * @param bidUserId user id of the bidder, must match the primary key of table users
   * @param bidItemId item id, must match the primary key of table items
   * @param userBid the amount of the user bid
   * @param userMaxBid the maximum amount the user wants to bid
   * @param quantity number of items the user wants to buy
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public BidPK ejbCreate(
    Integer bidUserId,
    Integer bidItemId,
    float userBid,
    float userMaxBid,
    int quantity)
    throws CreateException, RemoteException, RemoveException
  {
    Item item;
    InitialContext initialContext = null;
    // Find the item to update its maxBid and nbOfBids
    try
    {
      initialContext = new InitialContext();
      ItemHome iHome =
        (ItemHome) PortableRemoteObject.narrow(
          initialContext.lookup("ItemHome"),
          ItemHome.class);
      item = iHome.findByPrimaryKey(new ItemPK(bidItemId));
    }
    catch (Exception e)
    {
      throw new CreateException(
        "Error while getting item id "
          + bidItemId
          + " in BidBean: "
          + e
          + "<br>");
    }
    item.setMaxBid(userBid);
    item.addOneBid();

    // Connecting to IDManager Home interface thru JNDI
    IDManagerHome home = null;
    IDManager idManager = null;

    try
    {
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
      id = idManager.getNextBidID();
      userId = bidUserId;
      itemId = bidItemId;
      bid = userBid;
      maxBid = userMaxBid;
      qty = quantity;
      date = TimeManagement.currentDateToString();
    }
    catch (Exception e)
    {
      throw new EJBException("Cannot create id for bid: " + e);
    }
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement(
          "INSERT INTO bids VALUES ("
            + id.intValue()
            + ", \""
            + userId
            + "\", \""
            + itemId
            + "\", \""
            + qty
            + "\", \""
            + bid
            + "\", \""
            + maxBid
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
      throw new EJBException("Failed to create object bid: " + e);
    }
    return new BidPK(id);
  }

  /** This method does currently nothing */
  public void ejbPostCreate(
    Integer bidUserId,
    Integer bidItemId,
    float userBid,
    float userMaxBid,
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
      stmt = conn.prepareStatement("DELETE FROM bids WHERE id=?");
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
      throw new EJBException("Failed to remove object bid: " + e);
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
            "UPDATE bids SET user_id=?, item_id=?, qty=?, bid=?, max_bid=?, date=? WHERE id=?");
        stmt.setInt(1, userId.intValue());
        stmt.setInt(2, itemId.intValue());
        stmt.setInt(3, qty);
        stmt.setFloat(4, bid);
        stmt.setFloat(5, maxBid);
        stmt.setString(6, date);
        stmt.setInt(7, id.intValue());
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
        throw new EJBException("Failed to update the record for bid: " + e);
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
      BidPK pk = (BidPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT * FROM bids WHERE id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new EJBException("Object bid not found");
      }
      userId = new Integer(rs.getInt("user_id"));
      itemId = new Integer(rs.getInt("item_id"));
      qty = rs.getInt("qty");
      bid = rs.getInt("bid");
      maxBid = rs.getFloat("max_bid");
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
      throw new EJBException("Failed to update bid bean: " + e);
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

  /**
   * Display bid history information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printBidHistory() throws RemoteException
  {
    return "<TR><TD><a href=\""
      + BeanConfig.context
      + "/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="
      + userId
      + "\">"
      + getBidderNickName()
      + "<TD>"
      + bid
      + "<TD>"
      + date
      + "\n";
  }
}
