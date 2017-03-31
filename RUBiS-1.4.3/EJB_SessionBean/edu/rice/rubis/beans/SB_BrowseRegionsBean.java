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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.Serializable;
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

  /**
   * Get all the regions.
   *
   * @return a string that is the list of regions in html format
   * @since 1.1
   */
  public String getRegions() throws RemoteException
  {
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    String regionName;
    StringBuffer html = new StringBuffer();

    try 
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT name FROM regions");
      rs = stmt.executeQuery();
    }
    catch (SQLException e)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to executeQuery " +e);
    }
    try 
    {
      while (rs.next()) 
      {
        regionName = rs.getString("name");
        html.append(printRegion(regionName));
      };
      if (stmt != null) stmt.close();	// close statement
      if (conn != null) conn.close();	// release connection
    } 
    catch (Exception e) 
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to get the list of regions" +e);
    }
    return html.toString();
  }
  

  /** 
   * Region related printed functions
   *
   * @param name the name of the region to display
   * @return a string in html format
   * @since 1.1
   */

  public String printRegion(String name) throws RemoteException
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.BrowseCategories?region="+URLEncoder.encode(name)+"\">"+name+"</a><br>\n";

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
