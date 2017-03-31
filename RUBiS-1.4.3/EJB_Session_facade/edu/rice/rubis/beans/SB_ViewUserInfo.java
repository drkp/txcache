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
   * Get the comment related to a specific user.
   *
   * @param userHome an <code>UserHome</code> value
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getComments(UserHome userHome, Integer userId) throws RemoteException;

  /**
   * Get the information about a user.
   *
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getUserInfo(Integer userId) throws RemoteException;

  /** 
   * Comment header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentHeader() throws RemoteException;

  /** 
   * Comment printed function
   *
   * @param userName the name of the user who is the subject of the comments
   * @param comment the comment to display
   * @return a string in html format
   * @since 1.1
   */
  public String printComment(String userName, Comment comment) throws RemoteException;

  /** 
   * Comment footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentFooter() throws RemoteException;


}
