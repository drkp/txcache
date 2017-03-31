package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/** This is the Home interface of the User Bean */

public interface UserHome extends EJBHome {
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
   * @return pk primary key set to null
   *
   */
  public User create(String userFirstname, String userLastname, String userNickname, String userEmail, 
                     String userPassword, Integer userRegionId) throws CreateException, RemoteException, RemoveException;

  /**
   * This method is used to retrieve a User Item Bean from its primary key,
   * that is to say its id.
   *
   * @param id user id (primary key)
   *
   * @return the User if found else null
   */
  public User findByPrimaryKey(UserPK id) throws FinderException, RemoteException;

  /**
   * This method is used to retrieve a User from its nick name.
   *
   * @param nickName User nick name
   *
   * @return the User if found else null
   */
  public User findByNickName(String nickName) throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all users from the database!
   *
   * @return List of all bids (eventually empty)
   */
  public Collection findAllUsers() throws RemoteException, FinderException;
}
