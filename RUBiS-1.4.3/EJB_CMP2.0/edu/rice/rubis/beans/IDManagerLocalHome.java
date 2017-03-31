package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.EJBLocalHome;

/** This is the Home interface of the IDManager Bean */

public interface IDManagerLocalHome extends EJBLocalHome {

  /**
   * This method is used to create a new IDManager Bean.
   *
   * @return entity bean
   */
  public IDManagerLocal create() throws CreateException;


  /**
   * This method is used to retrieve a IDManager Bean from its primary key,
   * that is to say its id (in that case 0).
   *
   * @param id IDManager id (primary key)
   *
   * @return the IDManager if found else null
   */
  public IDManagerLocal findByPrimaryKey(IDManagerPK id) throws FinderException;

}
