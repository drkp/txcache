package edu.rice.rubis.beans;

import java.rmi.RemoteException;

import javax.ejb.EJBObject;

/**
 * This is the Remote Interface for the Comment Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface Comment extends EJBObject
{
  /**
   * Get comment's id.
   *
   * @return comment id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException;

  /**
   * Get the user id of the author of the comment
   *
   * @return author user id
   * @exception RemoteException if an error occurs
   */
  public Integer getFromUserId() throws RemoteException;

  /**
   * Get the user id of the user this comment is about.
   *
   * @return user id this comment is about
   * @exception RemoteException if an error occurs
   */
  public Integer getToUserId() throws RemoteException;

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   * @exception RemoteException if an error occurs
   */
  public Integer getItemId() throws RemoteException;

  /**
   * Get the rating associated to this comment.
   *
   * @return rating
   * @exception RemoteException if an error occurs
   */
  public float getRating() throws RemoteException;

  /**
   * Time of the Comment in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return comment time
   * @exception RemoteException if an error occurs
   */
  public String getDate() throws RemoteException;

  /**
   * Get the comment text.
   *
   * @return comment text
   * @exception RemoteException if an error occurs
   */
  public String getComment() throws RemoteException;

  /**
   * Set a new user identifier for the author of the comment. 
   * This id must match the primary key of the users table.
   *
   * @param id author user id
   * @exception RemoteException if an error occurs
   */
  public void setFromUserId(Integer id) throws RemoteException;

  /**
   * Set a new user identifier for the user this comment is about. 
   * This id must match the primary key of the users table.
   *
   * @param id user id comment is about
   * @exception RemoteException if an error occurs
   */
  public void setToUserId(Integer id) throws RemoteException;

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   * @exception RemoteException if an error occurs
   */
  public void setItemId(Integer id) throws RemoteException;

  /**
   * Set a new rating for the ToUserId.
   *
   * @param rating maximum comment price
   * @exception RemoteException if an error occurs
   */
  public void setRating(int rating) throws RemoteException;

  /**
   * Set a new date for this comment
   *
   * @param newDate comment date
   * @exception RemoteException if an error occurs
   */
  public void setDate(String newDate) throws RemoteException;

  /**
   * Set a new comment for ToUserId from FromUserId.
   *
   * @param newComment Comment
   * @exception RemoteException if an error occurs
   */
  public void setComment(String newComment) throws RemoteException;

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printComment(String userName) throws RemoteException;

}
