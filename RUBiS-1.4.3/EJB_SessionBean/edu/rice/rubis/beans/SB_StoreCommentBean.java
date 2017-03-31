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
 * This is a stateless session bean used to create e new comment for a user.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_StoreCommentBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;

  /**
   * Create a new comment and update the rating of the user.
   *
   * @param fromId id of the user posting the comment
   * @param toId id of the user who is the subject of the comment
   * @param itemId id of the item related to the comment
   * @param rating value of the rating for the user
   * @param comment text of the comment
   * @since 1.1
   */
  public void createComment(Integer fromId, Integer toId, Integer itemId, int rating, String comment) throws RemoteException
  {
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

    utx = sessionContext.getUserTransaction();
    try
    {
      utx.begin();
      try 
      {
        // create new comment
        String now = TimeManagement.currentDateToString();
        conn = dataSource.getConnection();
        stmt = conn.prepareStatement("INSERT INTO comments VALUES (NULL, \""+
                                     fromId.intValue()+
                                     "\", \""+toId.intValue()+"\", \""+itemId.intValue()+
                                     "\", \""+ rating+"\", \""+now+"\",\""+comment+"\")");

        stmt.executeUpdate();
        stmt.close();
      }
      catch (SQLException e)
      {
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
       throw new RemoteException("Error while storing the comment (got exception: " +e+")<br>");
      }
      // Try to find the user corresponding to the 'to' ID
      PreparedStatement pstmt = null;
      try
      {
        stmt = conn.prepareStatement("SELECT rating FROM users WHERE id=?");
        stmt.setInt(1, toId.intValue());
        rs = stmt.executeQuery();
        
        if (rs.first())
        {
          int userRating = rs.getInt("rating");
          userRating = userRating + rating;
          pstmt =conn.prepareStatement("UPDATE users SET rating=? WHERE id=?");
          pstmt.setInt(1, userRating);
          pstmt.setInt(2, toId.intValue());
          pstmt.executeUpdate();
          pstmt.close();
        }
        stmt.close();
      }
      catch (SQLException e)
      {
        try { pstmt.close(); } catch (Exception ignore) {}
        try { stmt.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        throw new RemoteException("Error while updating user's rating (got exception: " +e+")<br>");
      }
     
      utx.commit();
      conn.close();
    }
    catch (Exception e)
    {
      try { conn.close(); } catch (Exception ignore) {}
      try
      {
        utx.rollback();
        throw new RemoteException("Error while storing the comment (got exception: " +e+")<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
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
