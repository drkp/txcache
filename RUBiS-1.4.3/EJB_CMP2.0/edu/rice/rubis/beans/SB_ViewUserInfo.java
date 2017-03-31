package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the SB_ViewUserInfo Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_ViewUserInfo extends EJBObject, Remote {


  /**
   * Get the information about a user.
   *
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getUserInfo(Integer userId) throws RemoteException;


}
