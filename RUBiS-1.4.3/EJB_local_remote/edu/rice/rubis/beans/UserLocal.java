package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the User Bean.
 * @author <a href="mailto:">Emmanuel Cecchet</a>
 * @version 1.0
 */
public interface UserLocal extends EJBLocalObject {
  /**
   * Get user's id.
   *
   * @return user id
   */
  public Integer getId();

  /**
   * Get user first name.
   *
   * @return user first name
   */
  public String getFirstName();

  /**
   * Get user last name.
   *
   * @return user last name
   */
  public String getLastName();

  /**
   * Get user nick name. This name is unique for each user and is used for login.
   *
   * @return user nick name
   */
  public String getNickName();

  /**
   * Get user password.
   *
   * @return user password
   */
  public String getPassword();

  /**
   * Get user email address.
   *
   * @return user email address
   */
  public String getEmail();
  
  /**
   * Get user rating. The higher the rating is, the most reliable the user is.
   *
   * @return user rating
   */
  public int getRating();
  
  /**
   * Get user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each item to sell.
   *
   * @return user's account current balance
   */
  public float getBalance();

  /**
   * Get user creation date.
   *
   * @return user creation date
   */
  public String getCreationDate();
  
  /**
   * Get region identifier of user's region.
   *
   * @return region id of the user
   */
  public Integer getRegionId();
    

  /**
   * Set user's first name
   *
   * @param newName user first name
   */
  public void setFirstName(String newName);

  /**
   * Set user's last name
   *
   * @param newName user last name
   */
  public void setLastName(String newName);

  /**
   * Set user's nick name
   *
   * @param newName user nick name
   */
  public void setNickName(String newName);

  /**
   * Set user's password
   *
   * @param newPassword a <code>String</code> value
   */
  public void setPassword(String newPassword);

  /**
   * Set user's email address
   *
   * @param newEmail a <code>String</code> value
   */
  public void setEmail(String newEmail);

  /**
   * Set a new creation date for this user account
   *
   * @param newCreationDate new user account creation date
   */
  public void setCreationDate(String newCreationDate);

  /**
   * Set a new region identifier. This id must match
   * the primary key of the region table.
   *
   * @param id region id
   */
  public void setRegionId(Integer id);

  /**
   * Set user rating. The higher the rating is, the most reliable the user is.
   *
   * @param newRating new user rating
   */
  public void setRating(int newRating);

  /**
   * Update the current rating by adding a new value to it. This value can
   * be negative if someone wants to decrease the user rating.
   *
   * @param diff value to add to the rating
   */
  public void updateRating(int diff);
  
  /**
   * Set user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each sold item.
   *
   * @param newBalance set user's account current balance
   */
  public void setBalance(float newBalance);


  /**
   * Returns a string displaying general information about the user.
   * The string contains HTML tags.
   *
   * @return string containing general user information
   */
  public String getHTMLGeneralUserInformation();
}
