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
 * This is a stateless session bean used to get the information to build the html form
 * used to put a comment on a user. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_PutCommentBean implements SessionBean 
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
  public String getCommentForm(Integer itemId, Integer toId, String username, String password) throws RemoteException 
  {
    int userId             = -1;
    StringBuffer html      = new StringBuffer();
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
    // Try to find the user corresponding to the 'to' ID
    String toName=null, itemName=null;
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      stmt.setInt(1, toId.intValue());
      rs = stmt.executeQuery();
      if (rs.first())
        toName = rs.getString("nickname");
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
      throw new RemoteException("Failed to execute Query for user name: " +e);
    }

    try
    {
      stmt = conn.prepareStatement("SELECT name FROM items WHERE id=?");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
      if (rs.first())
        itemName = rs.getString("name");
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
      throw new RemoteException("Failed to execute Query for item name: " +e);
    }

    try
    {
      html.append("<center><h2>Give feedback about your experience with "+toName+"</h2><br>\n");
      html.append("<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreComment\" method=POST>\n");
      html.append("<input type=hidden name=to value="+toId.intValue()+">\n");
      html.append("<input type=hidden name=from value="+userId+">\n");
      html.append("<input type=hidden name=itemId value="+itemId.intValue()+">\n");
      html.append("<center><table>\n");
      html.append("<tr><td><b>From</b><td>"+username+"\n");
      html.append("<tr><td><b>To</b><td>"+toName+"\n");
      html.append("<tr><td><b>About item</b><td>"+itemName+"\n");
    }
    catch (Exception e)
    {
      throw new RemoteException("Cannot build comment form: " +e);
    }
 
    return html.toString();
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
