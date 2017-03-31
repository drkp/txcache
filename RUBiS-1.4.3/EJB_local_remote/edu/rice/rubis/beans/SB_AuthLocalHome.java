package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;

/** This is the local Home interface of the SB_Auth Bean */

public interface SB_AuthLocalHome extends EJBLocalHome {

  /**
   * This method is used to create a new Bean.
   *
   * @return session bean
   */
  public SB_AuthLocal create() throws CreateException;

}
