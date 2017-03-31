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
  private UserTransaction utx = null;

  /**
   * Get the comment related to a specific user.
   *
   * @param userHome an <code>UserHome</code> value
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getComments(UserHome userHome, Integer userId) throws RemoteException
  {
    Collection   list;
    StringBuffer html;
    CommentHome  cHome = null;
    Comment      comment = null;
    User         user = null;

    // Try to find the comments corresponding for this user

    try 
    {
      cHome = (CommentHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/Comment"),
                                                       CommentHome.class);
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup Comment: " +e+"<br>");
    }
    
    utx = sessionContext.getUserTransaction();

    try 
    {
      utx.begin();
      list = cHome.findByToUser(userId);
      if (list.isEmpty())
       html = new StringBuffer("<h3>There is no comment yet for this user.</h3><br>");
      else
      {
        html = new StringBuffer("<br><hr><br><h3>Comments for this user</h3><br>");

        html.append(printCommentHeader());
        // Display each comment and the name of its author
        Iterator it = list.iterator();
        while (it.hasNext())
        {
          comment = (Comment)it.next();
          String userName;
          try
          {
            user = userHome.findByPrimaryKey(new UserPK(comment.getFromUserId()));
            userName = user.getNickName();
          }
          catch (Exception e)
          {
            throw new RemoteException("This author does not exist (got exception: " +e+")<br>");
          }
          html.append(printComment(userName, comment));
        }
        html.append(printCommentFooter());
      }
      utx.commit();
    } 
    catch (Exception e) 
    {
      try
      {
        utx.rollback();
        throw new RemoteException("Exception getting comment list: " + e +"<br>");
      }
      catch (Exception se) 
      {
        throw new RemoteException("Transaction rollback failed: " + e +"<br>");
      }
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
    UserHome     uHome = null;
    User         user = null;
 

    // Try to find the user corresponding to the userId
    try 
    {
      uHome = (UserHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/User"),
                                                    UserHome.class);
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup User: " +e+"<br>");
    }
    try
    {
      user = uHome.findByPrimaryKey(new UserPK(userId));
      html.append(user.getHTMLGeneralUserInformation());
      html.append(getComments(uHome, userId));
    }
    catch (Exception e)
    {
      throw new RemoteException("Cannot get user information (got exception: " +e+")<br>");
    }
    return html.toString();
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
   * Comment printed function
   *
   * @param userName the name of the user who is the subject of the comments
   * @param comment the comment to display
   * @return a string in html format
   * @since 1.1
   */
  public String printComment(String userName, Comment comment) throws RemoteException
  {
    try
    {
      return comment.printComment(userName);
    }
    catch (RemoteException re)
    {
      throw new RemoteException("Unable to print Comment (exception: "+re+")<br>\n");
    }
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
