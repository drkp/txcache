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
import java.net.URLEncoder;

/**
 * This is a stateless session bean used to get the information about a user.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_ViewUserInfoBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;

  /**
   * Get the comment related to a specific user.
   *
   * @param userHome an <code>UserHome</code> value
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getComments(Integer userId, Connection conn) throws RemoteException
  {
    StringBuffer html;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    String comment=null, date=null;
    int authorId;

    // Try to find the comments corresponding for this user
    try
    {
      stmt = conn.prepareStatement("SELECT * FROM comments WHERE to_user_id=?");
      stmt.setInt(1, userId.intValue());
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
      throw new RemoteException("Failed to get the comments " +e);
    }
    try 
    {
      if (!rs.first())
      {
       html = new StringBuffer("<h3>There is no comment yet for this user.</h3><br>");
       stmt.close();
      }
      else
      {
        html = new StringBuffer("<br><hr><br><h3>Comments for this user</h3><br>");

        html.append(printCommentHeader());
        // Display each comment and the name of its author
        PreparedStatement pstmt = null;
        try
        {
          pstmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
          do
          {
            comment = rs.getString("comment");
            date = rs.getString("date");
            authorId = rs.getInt("from_user_id");

            String authorName = "none";
            ResultSet authorRS = null;
            pstmt.setInt(1, authorId);
            authorRS = pstmt.executeQuery();
            if (authorRS.first())
              authorName = authorRS.getString("nickname");
            html.append(printComment(authorName, date, comment, authorId));
          }
          while (rs.next());
           stmt.close();
           pstmt.close();
        }
        catch (Exception e)
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
          throw new RemoteException("This author does not exist (got exception: " +e+")<br>");
        }
        
        html.append(printCommentFooter());
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
      throw new RemoteException("Exception getting comment list: " + e +"<br>");
    }
    return html.toString();
  }


  /**
   * Get the information about a user.
   *
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getUserInfo(Integer userId) throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null; 
 

    // Try to find the user corresponding to the userId
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT * FROM users WHERE id=?");
      stmt.setInt(1, userId.intValue());
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
      throw new RemoteException("Failed to get user information from database: " +e);
    }

    try
    {
      if (rs.first())
      {
        String firstname = rs.getString("firstname");
        String lastname = rs.getString("lastname");
        String nickname = rs.getString("nickname");
        String email = rs.getString("email");
        String date = rs.getString("creation_date");
        int rating = rs.getInt("rating");

        html.append(getHTMLGeneralUserInformation(firstname, lastname, nickname, email, date, rating));
        html.append(getComments(userId, conn));
      }
      else
        html.append("This user does not exist!<br>");
      stmt.close();
      conn.close();
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
      throw new RemoteException("Cannot get user information (got exception: " +e+")<br>");
    }
    return html.toString();
  }

  /**
   * Returns a string displaying general information about the user.
   * The string contains HTML tags.
   *
   * @return string containing general user information
   * @exception RemoteException if an error occurs
   */
  public String getHTMLGeneralUserInformation(String firstname, String lastname, String nickname, String email, String creationDate, int rating) throws RemoteException
  {
    return "<h2>Information about "+nickname+"<br></h2>"+
      "Real life name : "+firstname+" "+lastname+"<br>"
      +"Email address  : "+email+"<br>"
      +"User since     : "+creationDate+"<br>"
      +"Current rating : <b>"+rating+"</b><br>";
  }

  /** 
   * Comment header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentHeader()
  {
    return "<DL>\n";
  }

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printComment(String userName, String date, String comment, int fromUserId) throws RemoteException
  {
    return "<DT><b><BIG><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+fromUserId+"\">"+userName+"</a></BIG></b>"+
      " wrote the "+date+"<DD><i>"+comment+"</i><p>\n";
  }

  /** 
   * Comment footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentFooter()
  {
    return "</DL>\n";
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
