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
 * This is a stateless session bean used to build the html form to buy an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_BuyNowBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;


  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBuyNowForm(Integer itemId, String username, String password) throws RemoteException
  {
    int userId = -1;
    StringBuffer html = new StringBuffer();
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

    // Authenticate the user who want to comment
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
    // Try to find the Item corresponding to the Item ID
    String itemName = null, description = null;
    String startDate = null, endDate = null, sellerName = null;
    int quantity = 0, sellerId = -1;
    float buyNow = 0;
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
      if (rs.first())
      {
        itemName = rs.getString("name");
        description = rs.getString("description");
        startDate = rs.getString("start_date");
        endDate = rs.getString("end_date");
        buyNow = rs.getFloat("buy_now");
        quantity = rs.getInt("quantity");
        sellerId = rs.getInt("seller");
      }
      stmt.close();
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
      throw new RemoteException("Failed to execute Query for item: " +e);
    }

    try
    {
      stmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      stmt.setInt(1, sellerId);
      ResultSet srs = stmt.executeQuery();
      if (srs.first())
      {
        sellerName = srs.getString("nickname");
      }
      stmt.close();
      conn.close();
     }
    catch (SQLException s)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to execute Query for seller: " +s);
    }

    // Display the form for buying the item
    html.append("<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>You are ready to buy this item: "+itemName+"</B></FONT></TD></TR>\n</TABLE><p>\n");
    try
    {
      html.append(printItemDescriptionToBuyNow(itemId.intValue(), itemName, description, buyNow, quantity, sellerId, sellerName, startDate, endDate, userId));
    }
    catch (Exception e)
    {
      throw new RemoteException("Unable to print Item description: " +e);
    }

    return html.toString();
  }
 
  /**
   * Display item information for the Buy Now servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemDescriptionToBuyNow(int itemId, String itemName, String description, float buyNow, int quantity, int sellerId, String sellerName, String startDate, String endDate, int userId) throws RemoteException
  {
    StringBuffer result = new StringBuffer("<TABLE>\n"+"<TR><TD>Quantity<TD><b><BIG>"+quantity+"</BIG></b>\n");
    result.append("<TR><TD>Seller<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+
                  sellerName+"</a> (<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutCommentAuth?to="+sellerId+"&itemId="+itemId+"\">Leave a comment on this user</a>)\n"+
                  "<TR><TD>Started<TD>"+startDate+"\n"+"<TR><TD>Ends<TD>"+endDate+"\n"+
                  "</TABLE>"+
                  "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n"+
                  "<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>Item description</B></FONT></TD></TR>\n"+
                  "</TABLE><p>\n"+description+"<br><p>\n"+
                  "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n"+
                  "<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>Buy Now</B></FONT></TD></TR>\n"+
                  "</TABLE><p>\n"+
                  "<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreBuyNow\" method=POST>\n"+
                  "<input type=hidden name=userId value="+userId+">\n"+
                  "<input type=hidden name=itemId value="+itemId+">\n"+
                  "<input type=hidden name=maxQty value="+quantity+">\n");
    if (quantity > 1)
      result.append("<center><table><tr><td>Quantity:</td>\n"+
                    "<td><input type=text size=5 name=qty></td></tr></table></center>\n");
    else
      result.append("<input type=hidden name=qty value=1>\n");
    result.append("<p><input type=submit value=\"Buy now!\"></center><p>\n");
    return result.toString();
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
