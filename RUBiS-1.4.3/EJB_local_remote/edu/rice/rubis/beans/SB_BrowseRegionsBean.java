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
import java.util.Collection;
import java.util.Iterator;
import java.net.URLEncoder;

/**
 * This is a stateless session bean used to get the list of regions
 * from the database. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_BrowseRegionsBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;


  /**
   * Get all the regions.
   *
   * @return a string that is the list of regions in html format
   * @since 1.1
   */
  public String getRegions() throws RemoteException
  {
    
    Collection list;
    RegionLocalHome home = null;
    RegionLocal reg;
    String html = "";

    // Connecting to Region Home
    try 
    {
      home = (RegionLocalHome)initialContext.lookup("java:comp/env/ejb/Region");
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup Region: " +e);
    }

    utx = sessionContext.getUserTransaction();

    try 
    {
      utx.begin();	
      list = home.findAllRegions();
      Iterator it = list.iterator();
      while (it.hasNext())
      {
        reg = (RegionLocal)it.next();
        html = html + printRegion(reg);
      }
      utx.commit();
    } 
    catch (Exception e) 
    {
      try
      {
        utx.rollback();
        throw new RemoteException("Exception getting region list: " + e);
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e);
      }
    }
    return html;
  }
  

  /** 
   * Region related printed functions
   *
   * @param region the region to display
   * @return a string in html format
   * @since 1.1
   */

  public String printRegion(RegionLocal region) throws RemoteException
  {
    String html;
    try
    {
      String name = region.getName();
      html = "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.BrowseCategories?region="+URLEncoder.encode(name)+"\">"+name+"</a><br>\n";
    }
    catch (EJBException re)
    {
      throw new EJBException("Unable to print Region (exception: "+re+")");
    }
    return html;
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
