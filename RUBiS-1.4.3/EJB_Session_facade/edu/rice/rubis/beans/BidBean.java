package edu.rice.rubis.beans;

import java.rmi.*;
import javax.ejb.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;

/**
 * BidBean is an entity bean with "container managed persistence". 
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

  /* Class member variables */

  public Integer id;
  public Integer userId;
  public Integer itemId;
  public int     qty;
  public float   bid;
  public float   maxBid;
  public String  date;

  
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
      uHome = (UserHome)PortableRemoteObject.narrow(initialContext.lookup("UserHome"),
                                                    UserHome.class);
    } 
    catch (Exception e)
    {
      System.err.print("Cannot lookup User: " +e);
      return null;
    }
    try
    {
      User u = uHome.findByPrimaryKey(new UserPK(userId));
      return u.getNickName();
    }
    catch (Exception e)
    {
      System.err.print("This user does not exist (got exception: " +e+")<br>");
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
  public BidPK ejbCreate(Integer bidUserId, Integer bidItemId, float userBid, float userMaxBid, int quantity) throws CreateException, RemoteException, RemoveException
  {
    Item item;
    InitialContext initialContext = null;
    // Find the item to update its maxBid and nbOfBids
    try
    {
      initialContext = new InitialContext();
      ItemHome iHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("ItemHome"), ItemHome.class);
      item = iHome.findByPrimaryKey(new ItemPK(bidItemId));
    }
    catch (Exception e) 
    {
      throw new CreateException("Error while getting item id "+bidItemId+" in BidBean: " + e+"<br>");
    }
    item.setMaxBid(userBid);
    item.addOneBid();

     // Connecting to IDManager Home interface thru JNDI
      IDManagerHome home = null;
      IDManager idManager = null;
      
      try 
      {
        home = (IDManagerHome)PortableRemoteObject.narrow(initialContext.lookup(
               "java:comp/env/ejb/IDManager"), IDManagerHome.class);
      } 
      catch (Exception e)
      {
        throw new EJBException("Cannot lookup IDManager: " +e);
      }
     try 
      {
        IDManagerPK idPK = new IDManagerPK();
        idManager = home.findByPrimaryKey(idPK);
        id = idManager.getNextBidID();
        userId = bidUserId;
        itemId = bidItemId;
        bid    = userBid;
        maxBid = userMaxBid;
        qty    = quantity;
        date   = TimeManagement.currentDateToString();
      } 
     catch (Exception e)
     {
       throw new EJBException("Cannot create bid: " +e);
     }
    return null;
  }


  /** This method just set an internal flag to 
      reload the id generated by the DB */
  public void ejbPostCreate(Integer bidUserId, Integer bidItemId, float userBid, float userMaxBid, int quantity) 
  {
    isDirty = true; // the id has to be reloaded from the DB
  }

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbLoad() throws RemoteException 
  {
    isDirty = false;
  }

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbStore() throws RemoteException
  {
    isDirty = false;
  }

  /** This method is empty because persistence is managed by the container */
  public void ejbActivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbPassivate() throws RemoteException  {}
  /** This method is empty because persistence is managed by the container */
  public void ejbRemove() throws RemoteException, RemoveException {}

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
   * Returns true if the beans has been modified.
   * It prevents the EJB server from reloading a bean
   * that has not been modified.
   *
   * @return a <code>boolean</code> value
   */
  public boolean isModified() 
  {
    return isDirty;
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
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+userId+
      "\">"+getBidderNickName()+"<TD>"+bid+"<TD>"+date+"\n";
  }
}
