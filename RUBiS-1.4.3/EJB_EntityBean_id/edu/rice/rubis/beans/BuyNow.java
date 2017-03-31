package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface for the BuyNow Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface BuyNow extends EJBObject {
  /**
   * Get BuyNow id.
   *
   * @return BuyNow id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException;

  /**
   * Get the buyer id which is the primary key in the users table.
   *
   * @return buyer id
   * @exception RemoteException if an error occurs
   */
  public Integer getBuyerId() throws RemoteException;

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   * @exception RemoteException if an error occurs
   */
  public Integer getItemId() throws RemoteException;

  /**
   * Get how many of this item the buyer has bought.
   *
   * @return quantity of items for this bid.
   * @exception RemoteException if an error occurs
   */
  public int getQuantity() throws RemoteException;

  /**
   * Time of the BuyNow in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return bid time
   * @exception RemoteException if an error occurs
   */
  public String getDate() throws RemoteException;

  /**
   * Set a new buyer identifier. This id must match
   * the primary key of the buyers table.
   *
   * @param id buyer id
   * @exception RemoteException if an error occurs
   */
  public void setBuyerId(Integer id) throws RemoteException;

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   * @exception RemoteException if an error occurs
   */
  public void setItemId(Integer id) throws RemoteException;

  /**
   * Set a new quantity for this buy
   *
   * @param Qty quantity
   * @exception RemoteException if an error occurs
   */
  public void setQuantity(int Qty) throws RemoteException;

  /**
   * Set a new date for this buy
   *
   * @param newDate bid date
   * @exception RemoteException if an error occurs
   */
  public void setDate(String newDate) throws RemoteException;
}
