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
 * This is a stateless session bean used to register a new user.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_RegisterUserBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;

  /**
   * Create a new user.
   *
   * @param firstname user's first name
   * @param lastname user's last name
   * @param nickname user's nick name
   * @param email user's email
   * @param password user's password
   * @param regionName name of the region where the user live
   * @return a string in html format
   * @since 1.1
   */
  public String createUser(String firstname, String lastname, String nickname, String email, String password, String regionName) throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    int regionId           = -1;
    int userId             = -1;
    int rating             = 0;
    float balance          = 0;
    String creationDate    = null;

    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT id FROM regions WHERE name=?");
      stmt.setString(1, regionName);
      rs = stmt.executeQuery();
      
      if (rs.first())
      {
        regionId = rs.getInt("id");
      }
      stmt.close();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException(" Region "+regionName+" does not exist in the database!<br>(got exception: " +e+")<br>\n");
    }
    utx = sessionContext.getUserTransaction();
    // Try to create a new user
    try
    {
      stmt = conn.prepareStatement("SELECT nickname FROM users WHERE nickname=?");
      stmt.setString(1, nickname);
      rs = stmt.executeQuery();
      if (rs.first())
      {
        html.append("The nickname you have choosen is already taken by someone else. Please choose a new nickname.<br>");
        return html.toString();
      }
      stmt.close();
      conn.close();
    }
    catch (Exception fe)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Failed to execute Query to check the nickname: " +fe);
    }
    try
    {
      utx.begin();
      try
      {
        creationDate = TimeManagement.currentDateToString();
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("INSERT INTO users VALUES (NULL, \""+firstname+
                                     "\", \""+lastname+"\", \""+nickname+"\", \""+
                                     password+"\", \""+email+"\", 0, 0,\""+creationDate+"\", "+ 
                                     regionId+")");
        stmt.executeUpdate();
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("RUBiS internal error: User registration failed (got exception: " +e+")<br>");
      }
      try
      {
        stmt = conn.prepareStatement("SELECT id, rating, balance FROM users WHERE nickname=?");
        stmt.setString(1, nickname);
        ResultSet urs = stmt.executeQuery();      
        if (urs.first())
        {
          userId = urs.getInt("id");
          rating = urs.getInt("rating");
          balance = urs.getFloat("balance");
        }
       stmt.close();       
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Failed to execute Query for user: " +e);
      }    
      utx.commit();
      conn.close();

      html.append("User id       :"+userId+"<br>\n");
      html.append("Creation date :"+creationDate+"<br>\n");
      html.append("Rating        :"+rating+"<br>\n");
      html.append("Balance       :"+balance+"<br>\n");
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      try
      {
        utx.rollback();
        throw new RemoteException("User registration failed (got exception: " +e+")<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
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
