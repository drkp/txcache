package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/** This is the LocalHome interface of the User Bean */

public interface UserLocalHome extends EJBLocalHome {
  /**
   * This method is used to create a new User Bean. The user id and the creationDate
   * are automatically set by the system.
   *
   * @param userFirstName user's first name
   * @param userLastName user's last name
   * @param userNickName user's nick name
   * @param userEmail email address of the user
   * @param userPassword user's password
   * @param userRegionId region id where the user lives
   *
   * @return the local interface on bean User
   *
   */
  public UserLocal create(String userFirstname, String userLastname, String userNickname, String userEmail, 
                     String userPassword, Integer userRegionId) throws CreateException, RemoveException;

  /**
   * This method is used to retrieve a User Item Bean from its primary key,
   * that is to say its id.
   *
   * @param id user id (primary key)
   *
   * @return the User if found else null
   */
  public UserLocal findByPrimaryKey(UserPK id) throws FinderException;

  /**
   * This method is used to retrieve a User from its nick name.
   *
   * @param nickName User nick name
   *
   * @return the User if found else null
   */
  public UserLocal findByNickName(String nickName) throws FinderException;

  /**
   * This method is used to retrieve all users from the database!
   *
   * @return List of all bids (eventually empty)
   */
  public Collection findAllUsers() throws FinderException;
}
