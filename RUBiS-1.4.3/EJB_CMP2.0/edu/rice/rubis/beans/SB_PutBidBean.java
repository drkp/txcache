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
 * This is a stateless session bean used to build the html form to put a bid.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_PutBidBean implements SessionBean 
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  //private UserTransaction utx = null;


  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBiddingForm(Integer itemId, String username, String password) throws RemoteException 
  {
    int userId = -1;
    String html = "";

    // Authenticate the user who want to comment
      if ((username != null && !username.equals("")) || (password != null && !password.equals("")))
      {
        SB_AuthLocalHome authHome = null;
        SB_AuthLocal auth = null;
        try 
        {
          authHome = (SB_AuthLocalHome)initialContext.lookup("java:comp/env/ejb/SB_Auth");
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
           html = (" You don't have an account on RUBiS!<br>You have to register first.<br>");
           return html;
        }
      }

      SB_ViewItemHome itemHome;
      SB_ViewItem viewItem;
      try 
      {
        itemHome = (SB_ViewItemHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/SB_ViewItem"),
                                                         SB_ViewItemHome.class);
        viewItem = itemHome.create();
      } 
      catch (Exception e)
      {
        throw new RemoteException("Cannot lookup SB_ViewItem: " +e+"<br>");
      }
      try
      {
        html = viewItem.getItemDescription(itemId, userId);
      } 
      catch (Exception e) 
      {
        throw new RemoteException("Exception getting the item information: "+ e +"<br>");
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
