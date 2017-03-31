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
import javax.transaction.UserTransaction;

/**
 * This is a stateless session bean used to register a new item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_RegisterItemBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;

  /**
   * Create a new item.
   *
   * @param name name of the item
   * @param description item's description
   * @param initialPrice item's initial price
   * @param quantity number of items
   * @param reservePrice item's reserve price
   * @param buyNow item's price to buy it now
   * @param startDate auction's start date
   * @param endDate auction's end date
   * @param userId seller id
   * @param catagoryId category id
   * @return a string in html format
   * @since 1.1
   */
  public String createItem(String name, String description, float initialPrice, int quantity, float reservePrice, float buyNow, String startDate, String endDate, int userId, int categoryId) throws RemoteException
  {
    String html;
    int itemId = -1;
    Connection        conn = null;
    PreparedStatement stmt = null;

    utx = sessionContext.getUserTransaction();
    // Try to create a new item
    try 
    {
      utx.begin();
      try 
      {
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("INSERT INTO items VALUES (NULL, \""+name+
                                     "\", \""+description+"\", \""+initialPrice+"\", \""+
                                     quantity+"\", \""+reservePrice+"\", \""+buyNow+
                                     "\", 0, 0, \""+startDate+"\", \""+endDate+"\", \""+userId+
                                     "\", "+ categoryId+")");
        stmt.executeUpdate();
        stmt.close();
      }
      catch (Exception e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to create the item: " +e);
      }
      // To test if the item was correctly added in the database
      try
      {
        stmt = conn.prepareStatement("SELECT id FROM items WHERE name=?");
        stmt.setString(1, name);
        ResultSet irs = stmt.executeQuery();
        if (!irs.first())
        {
          try { stmt.close(); } catch (Exception ignore) {}
          try { conn.close(); } catch (Exception ignore) {}
          throw new RemoteException("This item does not exist in the database.");
        }
        itemId = irs.getInt("id");
        
        html = "<TR><TD>Item id<TD>"+itemId+"\n";
      }
      catch (Exception e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to retrieve the item id: " +e);
      }
      if (stmt != null) stmt.close();
      if (conn != null) conn.close();
      utx.commit();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      try
      {
        utx.rollback();
        throw new RemoteException("Item registration failed (got exception: " +e+")<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
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
