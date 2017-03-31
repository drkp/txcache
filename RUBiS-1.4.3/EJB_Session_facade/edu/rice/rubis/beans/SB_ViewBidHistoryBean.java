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

/**
 * This is a stateless session bean used get the bid history of an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_ViewBidHistoryBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  //private UserTransaction utx = null;


  /**
   * Get the list of bids related to a specific item.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBidHistory(Integer itemId) throws RemoteException 
  {
    StringBuffer html;
    ItemHome     iHome;
    Item         item;
    Query        query;
    QueryHome    qHome;
    BidHome      bidHome;
    Bid          bid;
    Enumeration  bidList=null;

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
      item = iHome.findByPrimaryKey(new ItemPK(itemId));
      html = new StringBuffer("<center><h3>Bid History for "+item.getName()+"<br></h3></center>");
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup Item ("+itemId+"): " +e);
    }
    try 
    {
      query = qHome.create();
      bidList = query.getItemBidHistory(itemId).elements();
    }
    catch (Exception e)
    {
	throw new RemoteException("Exception getting bids list: " +e+"<br>");
    }
    if ((bidList == null) || (!bidList.hasMoreElements()))
    {
      return html.append("<h3>There is no bid corresponding to this item.</h3><br>").toString();
    }
    // Lookup bid home interface
    try 
    {
      bidHome = (BidHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/Bid"),
                                                     BidHome.class);
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup Bid: " +e+"<br>");
    }
    try
    {
      html.append(printBidHistoryHeader());
      while (bidList.hasMoreElements()) 
      {
        // Get the bids
        bid = bidHome.findByPrimaryKey((BidPK)bidList.nextElement());
        html.append(bid.printBidHistory());
      }
      html.append(printBidHistoryFooter());
    }
    catch (Exception e)
    {
      throw new RemoteException("Exception getting bid: " + e +"<br>");
    }
    return html.toString();
  }

  /** 
   * Bids list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */                   
  public String printBidHistoryHeader()
  {
    return "<TABLE border=\"1\" summary=\"List of bids\">\n<THEAD>\n"+
      "<TR><TH>User ID<TH>Bid amount<TH>Date of bid\n<TBODY>\n";
  }  

  /** 
   * Bids list footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printBidHistoryFooter()
  {
    return "</TABLE>\n";
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
