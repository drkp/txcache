package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Local Interface for the Comment Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface CommentLocal extends EJBLocalObject {
  /**
   * Get comment's id.
   *
   * @return comment id
   */
  public Integer getId();

  /**
   * Get the user id of the author of the comment
   *
   * @return author user id
   */
  public Integer getFromUserId();

  /**
   * Get the user id of the user this comment is about.
   *
   * @return user id this comment is about
   */
  public Integer getToUserId();

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   */
  public Integer getItemId();

  /**
   * Get the rating associated to this comment.
   *
   * @return rating
   */
  public int getRating();

  /**
   * Time of the Comment in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return comment time
   */
  public String getDate();
  
  /**
   * Get the comment text.
   *
   * @return comment text
   */
  public String getComment();


  /**
   * Set a new user identifier for the author of the comment. 
   * This id must match the primary key of the users table.
   *
   * @param id author user id
   */
  public void setFromUserId(Integer id);

  /**
   * Set a new user identifier for the user this comment is about. 
   * This id must match the primary key of the users table.
   *
   * @param id user id comment is about
   */
  public void setToUserId(Integer id);

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   */
  public void setItemId(Integer id);

  /**
   * Set a new rating for the ToUserId.
   *
   * @param rating maximum comment price
   */
  public void setRating(int rating);

  /**
   * Set a new date for this comment
   *
   * @param newDate comment date
   */
  public void setDate(String newDate);

  /**
   * Set a new comment for ToUserId from FromUserId.
   *
   * @param newComment Comment
   */
  public void setComment(String newComment);

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printComment(String userName);

}
