package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Collection;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/** This is the Home interface of the Comment Bean */

public interface CommentHome extends EJBHome
{
  /**
   * This method is used to create a new Comment Bean. 
   * The date is automatically set to the current date when the method is called.
   *
   * @param FromUserId user id of the comment author, must match the primary key of table users
   * @param ToUserId user id of the user this comment is about, must match the primary key of table users
   * @param ItemId item id, must match the primary key of table items
   * @param Rating user (ToUserId) rating given by the author (FromUserId)
   * @param Comment comment text
   *
   * @return pk primary key set to null
   */
  public Comment create(
    Integer FromUserId,
    Integer ToUserId,
    Integer ItemId,
    int Rating,
    String Comment)
    throws CreateException, RemoteException, RemoveException;

  /**
   * This method is used to retrieve a Comment Bean from its primary key,
   * that is to say its id.
   *
   * @param id Comment id (primary key)
   *
   * @return the Comment if found else null
   */
  public Comment findByPrimaryKey(CommentPK id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all Comment Beans related to one item.
   * You must provide the item id.
   *
   * @param id item id
   *
   * @return List of Comments found (eventually empty)
   */
  public Collection findByItem(Integer id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all Comment Beans belonging to
   * a specific author. You must provide the author user id.
   *
   * @param id user id
   *
   * @return List of Comments found (eventually empty)
   */
  public Collection findByFromUser(Integer id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all Comment Beans related to
   * a specific user. You must provide the user id.
   *
   * @param id user id
   *
   * @return List of Comments found (eventually empty)
   */
  public Collection findByToUser(Integer id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all comments from the database!
   *
   * @return List of all comments (eventually empty)
   */
  public Collection findAllComments() throws RemoteException, FinderException;
}
