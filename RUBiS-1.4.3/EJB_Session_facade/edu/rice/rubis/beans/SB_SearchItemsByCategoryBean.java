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
import java.util.Enumeration;
import java.util.Iterator;
import java.net.URLEncoder;

/**
 * This is a stateless session bean used to get the list of items
 * that belong to a specific category. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_SearchItemsByCategoryBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;


  /**
   * Get the items in a specific category.
   *
   * @return a string that is the list of items in html format
   * @since 1.1
   */
  public String getItems(Integer categoryId, int page, int nbOfItems) throws RemoteException
  {
    
    Enumeration list;
    ItemPK      itemPK;
    ItemHome    iHome;
    Item        item;
    Query       query;
    QueryHome   qHome;
    StringBuffer html = new StringBuffer(); 

    try
    {
      qHome = (QueryHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/Query"), QueryHome.class);
      query = qHome.create();
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup Query: " +e);
    }
    try 
    {
      iHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/Item"), ItemHome.class);
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup Item: " +e);
    }

    utx = sessionContext.getUserTransaction();

    try 
    {
      utx.begin();
      list = query.getCurrentItemsInCategory(categoryId, page*nbOfItems, nbOfItems).elements();
      while (list.hasMoreElements()) 
      {
        itemPK = (ItemPK)list.nextElement();
        item = iHome.findByPrimaryKey(itemPK);
        html.append(printItem(item));
      }
      utx.commit();
    } 
    catch (Exception e)
    {
      try
      {
        utx.rollback();
        throw new RemoteException("Cannot get items list: " +e);
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e);
      }
    }
    return html.toString();
  }


  /** 
   * Item related printed function
   *
   * @param item the item to display
   * @return a string in html format
   * @since 1.1
   */
  public String printItem(Item item) throws RemoteException
  {
    try
    {
      return item.printItem();
    }
    catch (RemoteException re)
    {
      throw new RemoteException("Unable to print Item (exception: "+re+")<br>\n");
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
