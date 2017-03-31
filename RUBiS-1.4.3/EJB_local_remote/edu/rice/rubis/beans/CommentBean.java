package edu.rice.rubis.beans;

import java.rmi.*;
import javax.ejb.*;
import javax.rmi.PortableRemoteObject;
import javax.naming.InitialContext;

/**
 * BidBean is an entity bean with "container managed persistence". 
 * The state of an instance is stored into a relational database. 
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE comments (
 *   id           INTEGER UNSIGNED NOT NULL UNIQUE,
 *   from_user_id INTEGER,
 *   to_user_id   INTEGER,
 *   item_id      INTEGER,
 *   rating       INTEGER,
 *   date         DATETIME,
 *   comment      TEXT
 *   PRIMARY KEY(id),
 *   INDEX from_user (from_user_id),
 *   INDEX to_user (to_user_id),
 *   INDEX item (item_id)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class CommentBean implements EntityBean 
{
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function
  
  /* Class member variables */

  public Integer id;
  public Integer fromUserId;
  public Integer toUserId;
  public Integer itemId;
  public int     rating;
  public String  date;
  public String  comment;


  /**
   * Get comment's id.
   *
   * @return comment id
   */
  public Integer getId()
    {
    return id;
  }

  /**
   * Get the user id of the author of the comment
   *
   * @return author user id
   */
  public Integer getFromUserId()
  {
    return fromUserId;
  }

  /**
   * Get the user id of the user this comment is about.
   *
   * @return user id this comment is about
   */
  public Integer getToUserId()
  {
    return toUserId;
  }

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   */
  public Integer getItemId()
  {
    return itemId;
  }

  /**
   * Get the rating associated to this comment.
   *
   * @return rating
   */
  public float getRating()
  {
    return rating;
  }

  /**
   * Time of the Comment in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return comment time
   */
  public String getDate()
  {
    return date;
  }
  
  /**
   * Get the comment text.
   *
   * @return comment text
   */
  public String getComment()
  {
    return comment;
  }


  /**
   * Set a new user identifier for the author of the comment. 
   * This id must match the primary key of the users table.
   *
   * @param id author user id
   */
  public void setFromUserId(Integer id)
  {
    fromUserId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new user identifier for the user this comment is about. 
   * This id must match the primary key of the users table.
   *
   * @param id user id comment is about
   */
  public void setToUserId(Integer id)
  {
    toUserId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   */
  public void setItemId(Integer id)
  {
    itemId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new rating for the ToUserId.
   *
   * @param Rating an <code>int</code> value
   */
  public void setRating(int Rating)
  {
    rating = Rating;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new date for this comment
   *
   * @param newDate comment date
   */
  public void setDate(String newDate)
  {
    date = newDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new comment for ToUserId from FromUserId.
   *
   * @param newComment Comment
   */
  public void setComment(String newComment)
  {
    comment = newComment;
    isDirty = true; // the bean content has been modified
  }


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
   * @exception CreateException if an error occurs
   */
  public CommentPK ejbCreate(Integer FromUserId, Integer ToUserId, Integer ItemId, int Rating, String Comment) throws CreateException
  {
      // Connecting to IDManager Home interface thru JNDI
      IDManagerLocalHome home = null;
      IDManagerLocal idManager = null;
      
      try 
      {
        InitialContext initialContext = new InitialContext();
        home = (IDManagerLocalHome)initialContext.lookup(
               "java:comp/env/ejb/IDManager");
      } 
      catch (Exception e)
      {
        throw new EJBException("Cannot lookup IDManager: " +e);
      }
     try 
      {
        IDManagerPK idPK = new IDManagerPK();
        idManager = home.findByPrimaryKey(idPK);
        id = idManager.getNextCommentID();
        fromUserId = FromUserId;
        toUserId   = ToUserId;
        itemId     = ItemId;
        rating     = Rating;
        date       = TimeManagement.currentDateToString();
        comment    = Comment;
      } 
      catch (Exception e)
      {
        throw new EJBException("Cannot create comment: " +e);
      }
    return null;
  }

  /** This method just set an internal flag to 
      reload the id generated by the DB */
  public void ejbPostCreate(Integer FromUserId, Integer ToUserId, Integer ItemId, int Rating, String Comment)
  {
    isDirty = true; // the id has to be reloaded from the DB
  }

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbLoad() 
  {
    isDirty = false;
  }

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbStore()
  {
    isDirty = false;
  }

  /** This method is empty because persistence is managed by the container */
  public void ejbActivate()  {}
  /** This method is empty because persistence is managed by the container */
  public void ejbPassivate() {}
  /** This method is empty because persistence is managed by the container */
  public void ejbRemove() throws RemoveException {}

  /**
   * Sets the associated entity context. The container invokes this method 
   *  on an instance after the instance has been created. 
   * 
   * This method is called in an unspecified transaction context. 
   * 
   * @param context An EntityContext interface for the instance. The instance should 
   *                store the reference to the context in an instance variable. 
   * @exception EJBException  Thrown by the method to indicate a failure 
   *                          caused by a system-level error.
   */
  public void setEntityContext(EntityContext context) 
  {
    entityContext = context;
  }

  /**
   * Unsets the associated entity context. The container calls this method 
   *  before removing the instance. This is the last method that the container 
   *  invokes on the instance. The Java garbage collector will eventually invoke 
   *  the finalize() method on the instance. 
   *
   * This method is called in an unspecified transaction context. 
   * 
   * @exception EJBException  Thrown by the method to indicate a failure 
   *                          caused by a system-level error.
   */
  public void unsetEntityContext()
  {
    entityContext = null;
  }

  /**
   * Returns true if the beans has been modified.
   * It prevents the EJB server from reloading a bean
   * that has not been modified.
   *
   * @return a <code>boolean</code> value
   */
  public boolean isModified() 
  {
    return isDirty;
  }


  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printComment(String userName)
  {
    return "<DT><b><BIG><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+fromUserId+"\">"+userName+"</a></BIG></b>"+
      " wrote the "+date+"<DD><i>"+comment+"</i><p>\n";
  }
}
