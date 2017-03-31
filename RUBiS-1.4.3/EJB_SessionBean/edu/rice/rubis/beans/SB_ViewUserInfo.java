package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;
import java.sql.Connection;

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
  public String getComments(Integer userId, Connection conn) throws RemoteException;

  /**
   * Get the information about a user.
   *
   * @param userId a user id
   * @return a string in html format
   * @since 1.1
   */
  public String getUserInfo(Integer userId) throws RemoteException;

  /**
   * Returns a string displaying general information about the user.
   * The string contains HTML tags.
   *
   * @return string containing general user information
   * @exception RemoteException if an error occurs
   */
  public String getHTMLGeneralUserInformation(String firstname, String lastname, String nickname, String email, String creationDate, int rating) throws RemoteException;

  /** 
   * Comment header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentHeader() throws RemoteException;

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printComment(String userName, String date, String comment, int fromUserId) throws RemoteException;

  /** 
   * Comment footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentFooter() throws RemoteException;


}
