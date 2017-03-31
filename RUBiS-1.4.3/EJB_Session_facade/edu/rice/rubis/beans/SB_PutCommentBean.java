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
//  private UserTransaction utx = null;


  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getCommentForm(Integer itemId, Integer toId, String username, String password) throws RemoteException 
  {
    int userId = -1;
    String html = "";
    UserHome uHome;
    ItemHome iHome;

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
           html = (" You don't have an account on RUBiS!<br>You have to register first.<br>");
           return html;
        }
      }
    // Try to find the user corresponding to the 'to' ID
    try 
    {
      uHome = (UserHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/User"),
                                                    UserHome.class);
      iHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("java:comp/env/ejb/Item"),
                                                    ItemHome.class);
    } 
    catch (Exception e)
    {
      throw new RemoteException("Cannot lookup User or Item: " +e+"<br>");
    }
    try
    {
      User    to = uHome.findByPrimaryKey(new UserPK(toId));
      Item    item = iHome.findByPrimaryKey(new ItemPK(itemId));
      String  toName = to.getNickName();

      html = "<center><h2>Give feedback about your experience with "+toName+"</h2><br>\n" +
        "<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreComment\" method=POST>\n"+
        "<input type=hidden name=to value="+toId.intValue()+">\n"+
        "<input type=hidden name=from value="+userId+">\n"+
        "<input type=hidden name=itemId value="+itemId.intValue()+">\n"+
        "<center><table>\n"+
        "<tr><td><b>From</b><td>"+username+"\n"+
        "<tr><td><b>To</b><td>"+toName+"\n"+
        "<tr><td><b>About item</b><td>"+item.getName()+"\n";
    }
    catch (Exception e)
    {
      throw new RemoteException("Cannot build comment form: " +e);
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
