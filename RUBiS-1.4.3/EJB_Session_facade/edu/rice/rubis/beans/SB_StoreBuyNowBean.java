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
import java.io.Serializable;
import javax.transaction.UserTransaction;

/**
 * This is a stateless session bean used when a user buy an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_StoreBuyNowBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;

  /**
   * Create a buyNow and update the item.
   *
   * @param itemId id of the item related to the comment
   * @param userId id of the buyer
   * @param qty quantity of items
   * @since 1.1
   */
  public void createBuyNow(Integer itemId, Integer userId, int qty) throws RemoteException
  {
    utx = sessionContext.getUserTransaction();
    // Try to find the Item corresponding to the Item ID
    Item item;
    try 
    {
      utx.begin();
      ItemHome itemHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/Item"),
                                                                ItemHome.class);
      item = itemHome.findByPrimaryKey(new ItemPK(itemId));
      item.setQuantity(item.getQuantity() - qty);
      if (item.getQuantity() == 0)
        item.setEndDate(TimeManagement.currentDateToString());
    } 
    catch (Exception e)
    {
      try
      {
        utx.rollback();
        throw new RemoteException("Cannot update Item: " +e+"<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
    }
    try
    {
      BuyNowHome bHome = (BuyNowHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/BuyNow"),
                                                      BuyNowHome.class);
      edu.rice.rubis.beans.BuyNow b = bHome.create(userId, itemId, qty);     
      utx.commit();
    }
    catch (Exception e)
    {
      try
      {
        utx.rollback();
        throw new RemoteException("Error while storing the comment (got exception: " +e+")<br>");
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
