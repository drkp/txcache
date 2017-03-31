package edu.rice.rubis.beans;

import java.rmi.*;
import javax.ejb.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;

/**
 * BuyNowBean is an entity bean with "container managed persistence". 
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

  /* Class member variables */

  public Integer id;
  public Integer buyerId;
  public Integer itemId;
  public int     qty;
  public String  date;


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
  public BuyNowPK ejbCreate(Integer BuyNowUserId, Integer BuyNowItemId, int quantity) throws CreateException, RemoteException, RemoveException
  {
     // Connecting to IDManager Home interface thru JNDI
      IDManagerHome home = null;
      IDManager idManager = null;
      
      try 
      {
        InitialContext initialContext = new InitialContext();
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
        id = idManager.getNextBuyNowID();
        buyerId = BuyNowUserId;
        itemId  = BuyNowItemId;
        qty     = quantity;
        date    = TimeManagement.currentDateToString();
      } 
     catch (Exception e)
     {
        throw new EJBException("Cannot create buyNow: " +e);
      }
    return null;
  }

  /** This method does currently nothing */
  public void ejbPostCreate(Integer BuyNowUserId, Integer BuyNowItemId, int quantity) {}
  /** This method is empty because persistence is managed by the container */
  public void ejbActivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbPassivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbRemove() throws RemoteException, RemoveException {}

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbStore() throws RemoteException
  {
    isDirty = false;
  }
  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbLoad() throws RemoteException
  {
    isDirty = false;
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
