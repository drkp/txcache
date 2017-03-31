package edu.rice.rubis.beans;

import java.rmi.RemoteException;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/** This is the Home interface of the IDManager Bean */

public interface IDManagerHome extends EJBHome
{

  /**
   * This method is used to create a new IDManager Bean.
   *
   * @return entity bean
   */
  public IDManager create()
    throws CreateException, RemoteException, RemoveException;

  /**
   * This method is used to retrieve a IDManager Bean from its primary key,
   * that is to say its id (in that case 0).
   *
   * @param id IDManager id (primary key)
   *
   * @return the IDManager if found else null
   */
  public IDManager findByPrimaryKey(IDManagerPK id)
    throws FinderException, RemoteException;

}
