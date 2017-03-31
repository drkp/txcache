package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Properties;
import javax.ejb.*;
import java.io.Serializable;

/**
 * IDManagerBean is used to generate id since the AUTO_INCREMENT
 * feature of the database that automatically generate id on the primary key 
 * is not supported by JBoss. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class IDManagerBean implements EntityBean 
{
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function

  /* Class member variables */

  public Integer id;

  public Integer categoryCount;
  public Integer regionCount;
  public Integer userCount;
  public Integer itemCount;
  public Integer commentCount;
  public Integer bidCount;
  public Integer buyNowCount;

  

  /** 
   * Generate the category id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextCategoryID() throws RemoteException
  {
    categoryCount = new Integer(categoryCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return categoryCount;
  }

  /** 
   * Generate the region id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextRegionID() throws RemoteException
  {
    regionCount = new Integer(regionCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return regionCount;
  }

  /** 
   * Generate the user id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextUserID() throws RemoteException
  {
    userCount = new Integer(userCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return userCount;
  }

  /** 
   * Generate the item id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextItemID() throws RemoteException
  {
    itemCount = new Integer(itemCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return itemCount;
  }

  /** 
   * Generate the comment id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextCommentID() throws RemoteException
  {
    commentCount = new Integer(commentCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return commentCount;
  }

  /** 
   * Generate the bid id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextBidID() throws RemoteException
  {
    bidCount = new Integer(bidCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return bidCount;
  }

  /** 
   * Generate the buyNow id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextBuyNowID() throws RemoteException
  {
    buyNowCount = new Integer(buyNowCount.intValue()+1);
    isDirty = true; // the bean content has been modified
    return buyNowCount;
  }


  // ======================== EJB related methods ============================

  /**
   * This method is used to create a new IDManager Bean but should never be called.
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public IDManagerPK ejbCreate() throws CreateException, RemoteException, RemoveException
  {
    throw new CreateException();    
  }


  /** This method does currently nothing */
  public void ejbPostCreate() {}
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
   * @param context - An EntityContext interface for the instance. The instance should 
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


}
