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
    // Try to find the user corresponding to the 'to' ID
    User to;
    try 
    {
      UserHome uHome = (UserHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/User"),
                                                    UserHome.class);
      to = uHome.findByPrimaryKey(new UserPK(toId));
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup User ("+toId+"): " +e+"<br>");
    }
    CommentHome cHome;
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
      Comment c = cHome.create(fromId, toId, itemId, rating, comment);
      to.updateRating(rating);
      utx.commit();
    }
    catch (Exception e)
    {
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
