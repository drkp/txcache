package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the User Bean.
 * @author <a href="mailto:">Emmanuel Cecchet</a>
 * @version 1.0
 */
public interface User extends EJBObject {
  /**
   * Get user's id.
   *
   * @return user id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException;

  /**
   * Get user first name.
   *
   * @return user first name
   * @exception RemoteException if an error occurs
   */
  public String getFirstName() throws RemoteException;

  /**
   * Get user last name.
   *
   * @return user last name
   * @exception RemoteException if an error occurs
   */
  public String getLastName() throws RemoteException;

  /**
   * Get user nick name. This name is unique for each user and is used for login.
   *
   * @return user nick name
   * @exception RemoteException if an error occurs
   */
  public String getNickName() throws RemoteException;

  /**
   * Get user password.
   *
   * @return user password
   * @exception RemoteException if an error occurs
   */
  public String getPassword() throws RemoteException;

  /**
   * Get user email address.
   *
   * @return user email address
   * @exception RemoteException if an error occurs
   */
  public String getEmail() throws RemoteException;
  
  /**
   * Get user rating. The higher the rating is, the most reliable the user is.
   *
   * @return user rating
   * @exception RemoteException if an error occurs
   */
  public int getRating() throws RemoteException;
  
  /**
   * Get user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each item to sell.
   *
   * @return user's account current balance
   * @exception RemoteException if an error occurs
   */
  public float getBalance() throws RemoteException;

  /**
   * Get user creation date.
   *
   * @return user creation date
   * @exception RemoteException if an error occurs
   */
  public String getCreationDate() throws RemoteException;
  
  /**
   * Get region identifier of user's region.
   *
   * @return region id of the user
   * @exception RemoteException if an error occurs
   */
  public Integer getRegionId() throws RemoteException;
    

  /**
   * Set user's first name
   *
   * @param newName user first name
   * @exception RemoteException if an error occurs
   */
  public void setFirstName(String newName) throws RemoteException;

  /**
   * Set user's last name
   *
   * @param newName user last name
   * @exception RemoteException if an error occurs
   */
  public void setLastName(String newName) throws RemoteException;

  /**
   * Set user's nick name
   *
   * @param newName user nick name
   * @exception RemoteException if an error occurs
   */
  public void setNickName(String newName) throws RemoteException;

  /**
   * Set user's password
   *
   * @param newPassword a <code>String</code> value
   * @exception RemoteException if an error occurs
   */
  public void setPassword(String newPassword) throws RemoteException;

  /**
   * Set user's email address
   *
   * @param newEmail a <code>String</code> value
   * @exception RemoteException if an error occurs
   */
  public void setEmail(String newEmail) throws RemoteException;

  /**
   * Set a new creation date for this user account
   *
   * @param newCreationDate new user account creation date
   * @exception RemoteException if an error occurs
   */
  public void setCreationDate(String newCreationDate) throws RemoteException;

  /**
   * Set a new region identifier. This id must match
   * the primary key of the region table.
   *
   * @param id region id
   * @exception RemoteException if an error occurs
   */
  public void setRegionId(Integer id) throws RemoteException;

  /**
   * Set user rating. The higher the rating is, the most reliable the user is.
   *
   * @param newRating new user rating
   * @exception RemoteException if an error occurs
   */
  public void setRating(int newRating) throws RemoteException;

  /**
   * Update the current rating by adding a new value to it. This value can
   * be negative if someone wants to decrease the user rating.
   *
   * @param diff value to add to the rating
   * @exception RemoteException if an error occurs
   */
  public void updateRating(int diff) throws RemoteException;
  
  /**
   * Set user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each sold item.
   *
   * @param newBalance set user's account current balance
   * @exception RemoteException if an error occurs
   */
  public void setBalance(float newBalance) throws RemoteException;


  /**
   * Returns a string displaying general information about the user.
   * The string contains HTML tags.
   *
   * @return string containing general user information
   * @exception RemoteException if an error occurs
   */
  public String getHTMLGeneralUserInformation() throws RemoteException;
}
