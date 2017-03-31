package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the Region Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface Region extends EJBObject {
  /**
   * Get region's id.
   *
   * @return region id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException;

  /**
   * Get the region name.
   *
   * @return region name
   * @exception RemoteException if an error occurs
   */
  public String getName() throws RemoteException;

  /**
   * Set region's name
   *
   * @param newName region name
   * @exception RemoteException if an error occurs
   */
  public void setName(String newName) throws RemoteException;
}
