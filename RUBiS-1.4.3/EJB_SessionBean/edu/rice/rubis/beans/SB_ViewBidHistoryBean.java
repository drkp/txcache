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


  /**
   * Get the list of bids related to a specific item.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBidHistory(Integer itemId) throws RemoteException 
  {
    StringBuffer html = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;
    String date = null, bidderName = null, itemName = null;
    float bid = 0;
    int userId = -1;

    // get the item
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT name FROM items WHERE id=?");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
      
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
      throw new RemoteException("Failed to execute Query for item in items table: " +e);
    }
    try 
    {
      if (!rs.first())
      {
        stmt.close();
        stmt = conn.prepareStatement("SELECT name FROM old_items WHERE id=?");
        stmt.setInt(1, itemId.intValue());
        rs = stmt.executeQuery();
        
      }
     
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
      throw new RemoteException("Failed to execute Query for item in old_items table: " +e);
    }
    try 
    {
      if (rs.first())
      {
        itemName = rs.getString("name");
        html = new StringBuffer("<center><h3>Bid History for "+itemName+"<br></h3></center>");
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
     throw new RemoteException("This item does not exist (got exception: " +e+")<br>");
    }
    // Get the list of the user's last bids
    try 
    {
      stmt = conn.prepareStatement("SELECT * FROM bids WHERE item_id=? ORDER BY date DESC");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
      
      if (!rs.first())
      {
        stmt.close();
        conn.close();
        return html.append("<h3>There is no bid corresponding to this item.</h3><br>").toString();
      }
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
      throw new RemoteException("Exception getting bids list: " +e+"<br>");
    }
    PreparedStatement pstmt = null;
    try
    {	  
      html.append(printBidHistoryHeader());
      pstmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      ResultSet urs = null;
      do 
      {
        // Get the bids
        date = rs.getString("date");
        bid = rs.getFloat("bid");
        userId = rs.getInt("user_id");

        pstmt.setInt(1, userId);
        urs = pstmt.executeQuery();
        if (urs.first())
          bidderName = urs.getString("nickname");

        html.append(printBidHistory(userId, bidderName, bid, date));
      }
      while(rs.next());
      html.append(printBidHistoryFooter());
      pstmt.close();
      stmt.close();
      conn.close();
    }
    catch (SQLException e)
    {
      try
      {
        if (pstmt != null) pstmt.close();
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Exception getting bid: " +e+"<br>");
    }
    return html.toString();
  }

  /**
   * Display bid history information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printBidHistory(int userId, String bidderName, float bid, String date) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+userId+
      "\">"+bidderName+"<TD>"+bid+"<TD>"+date+"\n";
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
