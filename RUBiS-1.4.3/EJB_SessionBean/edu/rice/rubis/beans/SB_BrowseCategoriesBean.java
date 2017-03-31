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
 * This is a stateless session bean used to get the list of 
 * categories from database and return the information to the BrowseRegions servlet. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_BrowseCategoriesBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;


  /**
   * Get all the categories from the database.
   *
   * @return a string that is the list of categories in html format
   * @since 1.1
   */
  /** List all the categories in the database */
  public String getCategories(String regionName, String username, String password) throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    String categoryName;
    int categoryId;
    int regionId = -1;
    int userId = -1;

    if (regionName != null && regionName !="")
    {
      // get the region ID
      try 
      {
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("SELECT id FROM regions WHERE name=?");
        stmt.setString(1, regionName);
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
        throw new RemoteException("Failed to get region Id " +e);
      }
      try
      {
        if (rs.first())
        {
          regionId = rs.getInt("id");
        }
        stmt.close();
      }
      catch (Exception e)
      {
        try
        {
          if (conn != null) conn.close();
        }
        catch (Exception ignore)
        {
        }
        throw new RemoteException(" Region "+regionName+" does not exist in the database!<br>(got exception: " +e+")");
      }
    }
    else
    {
      // Authenticate the user who wants to sell items
      if ((username != null && !username.equals("")) || (password != null && !password.equals("")))
      {
        SB_AuthHome authHome = null;
        SB_Auth auth = null;
        try 
        {
          authHome = (SB_AuthHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/SB_Auth"), SB_AuthHome.class);
          auth = authHome.create();
        } 
        catch (Exception e)
        {
          throw new RemoteException("Cannot lookup SB_Auth: " +e);
        }
        try 
        {
          userId = auth.authenticate(username, password);
        } 
        catch (Exception e)
        {
          throw new RemoteException("Authentication failed: " +e);
        }
        if (userId == -1)
        {
           html.append(" You don't have an account on RUBiS!<br>You have to register first.<br>");
           return html.toString();
        }
      }
    }
    try 
    {
      if (conn == null)
        conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT name, id FROM categories");
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
      throw new RemoteException("Failed to get categories list " +e);
    }
    try 
    {
      if (!rs.first())
        html.append("<h2>Sorry, but there is no category available at this time. Database table is empty</h2><br>");
      else
      {
        do
        {
          categoryName = rs.getString("name");
          categoryId = rs.getInt("id");
          if (regionId != -1)
          {
            html.append(printCategoryByRegion(categoryName, categoryId, regionId));
          }
          else
          {
            if (userId != -1)
              html.append(printCategoryToSellItem(categoryName, categoryId, userId));
            else
              html.append(printCategory(categoryName, categoryId));
          }
        }
        while (rs.next());
      }
      if (stmt != null) stmt.close();
      if (conn != null) conn.close();
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
      throw new RemoteException("Exception getting category list: " + e);
    }
    return html.toString();
  }

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategory(String name, int id) throws RemoteException
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+id+
                  "&categoryName="+URLEncoder.encode(name)+"\">"+name+"</a><br>\n";
  }

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategoryByRegion(String name, int id, int regionId) throws RemoteException
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="+id+
      "&categoryName="+URLEncoder.encode(name)+"&region="+regionId+"\">"+name+"</a><br>\n";
  }


  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategoryToSellItem(String name, int id, int userId) throws RemoteException
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.SellItemForm?category="+id+"&user="+userId+"\">"+name+"</a><br>\n";
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
