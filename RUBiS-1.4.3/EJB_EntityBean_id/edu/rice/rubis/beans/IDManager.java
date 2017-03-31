package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the IDManager Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface IDManager extends EJBObject, Remote {

  /** 
   * Generate the category id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextCategoryID() throws RemoteException;

  /** 
   * Generate the region id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextRegionID() throws RemoteException;

  /** 
   * Generate the user id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextUserID() throws RemoteException;

  /** 
   * Generate the item id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextItemID() throws RemoteException;

  /** 
   * Generate the comment id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextCommentID() throws RemoteException;

  /** 
   * Generate the bid id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextBidID() throws RemoteException;

  /** 
   * Generate the buyNow id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextBuyNowID() throws RemoteException;

}
