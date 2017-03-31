package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedList;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;

/**
 * BidBean is an entity bean with "bean managed persistence". 
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
  private Context initialContext;
  private DataSource datasource;
  private transient boolean isDirty; // used for the isModified function

  /* Class member variables */

  public Integer id;
  public Integer fromUserId;
  public Integer toUserId;
  public Integer itemId;
  public int rating;
  public String date;
  public String comment;

  /**
   * Get comment's id.
   *
   * @return comment id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get the user id of the author of the comment
   *
   * @return author user id
   * @exception RemoteException if an error occurs
   */
  public Integer getFromUserId() throws RemoteException
  {
    return fromUserId;
  }

  /**
   * Get the user id of the user this comment is about.
   *
   * @return user id this comment is about
   * @exception RemoteException if an error occurs
   */
  public Integer getToUserId() throws RemoteException
  {
    return toUserId;
  }

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   * @exception RemoteException if an error occurs
   */
  public Integer getItemId() throws RemoteException
  {
    return itemId;
  }

  /**
   * Get the rating associated to this comment.
   *
   * @return rating
   * @exception RemoteException if an error occurs
   */
  public float getRating() throws RemoteException
  {
    return rating;
  }

  /**
   * Time of the Comment in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return comment time
   * @exception RemoteException if an error occurs
   */
  public String getDate() throws RemoteException
  {
    return date;
  }

  /**
   * Get the comment text.
   *
   * @return comment text
   * @exception RemoteException if an error occurs
   */
  public String getComment() throws RemoteException
  {
    return comment;
  }

  /**
   * Set a new user identifier for the author of the comment. 
   * This id must match the primary key of the users table.
   *
   * @param id author user id
   * @exception RemoteException if an error occurs
   */
  public void setFromUserId(Integer id) throws RemoteException
  {
    fromUserId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new user identifier for the user this comment is about. 
   * This id must match the primary key of the users table.
   *
   * @param id user id comment is about
   * @exception RemoteException if an error occurs
   */
  public void setToUserId(Integer id) throws RemoteException
  {
    toUserId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   * @exception RemoteException if an error occurs
   */
  public void setItemId(Integer id) throws RemoteException
  {
    itemId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new rating for the ToUserId.
   *
   * @param Rating an <code>int</code> value
   * @exception RemoteException if an error occurs
   */
  public void setRating(int Rating) throws RemoteException
  {
    rating = Rating;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new date for this comment
   *
   * @param newDate comment date
   * @exception RemoteException if an error occurs
   */
  public void setDate(String newDate) throws RemoteException
  {
    date = newDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new comment for ToUserId from FromUserId.
   *
   * @param newComment Comment
   * @exception RemoteException if an error occurs
   */
  public void setComment(String newComment) throws RemoteException
  {
    comment = newComment;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Retieve a connection..
   *
   * @return connection
   */
  public Connection getConnection() throws Exception
  {
    try
    {
      if (datasource == null)
      {
        // Finds DataSource from JNDI
        initialContext = new InitialContext();
        datasource =
          (DataSource) initialContext.lookup("java:comp/env/jdbc/rubis");
      }
      return datasource.getConnection();
    }
    catch (Exception e)
    {
      throw new Exception("Cannot retrieve the connection.");
    }
  }

  /**
   * This method is used to retrieve a Comment Bean from its primary key,
   * that is to say its id.
   *
   * @param id Comment id (primary key)
   *
   * @return the Comment primay key if found else null
   */
  public CommentPK ejbFindByPrimaryKey(CommentPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT comment FROM comments WHERE id=?");
      stmt.setInt(1, id.getId().intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        return null;
      }
      rs.close();
      stmt.close();
      conn.close();
      return id;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new FinderException("Failed to retrieve object comment: " + e);
    }
  }

  /**
   * This method is used to retrieve all Comment Beans related to one item.
   * You must provide the item id.
   *
   * @param id item id
   *
   * @return List of Comments primary keys found (eventually empty)
   */
  public Collection ejbFindByItem(Integer id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM comments WHERE item_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new CommentPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new FinderException("Failed to get all comments by items: " + e);
    }
  }

  /**
   * This method is used to retrieve all Comment Beans belonging to
   * a specific author. You must provide the author user id.
   *
   * @param id user id
   *
   * @return List of Comments primary keys found (eventually empty)
   */
  public Collection ejbFindByFromUser(Integer id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement("SELECT id FROM comments WHERE from_user_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new CommentPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new FinderException("Failed to get all comments by author: " + e);
    }
  }

  /**
   * This method is used to retrieve all Comment Beans related to
   * a specific user. You must provide the user id.
   *
   * @param id user id
   *
   * @return List of Comments primary keys found (eventually empty)
   */
  public Collection ejbFindByToUser(Integer id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement("SELECT id FROM comments WHERE to_user_id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      LinkedList results = new LinkedList();
      int pk;
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new CommentPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new FinderException("Failed to get all comments by toUser: " + e);
    }
  }

  /**
   * This method is used to retrieve all comments from the database!
   *
   * @return List of all comments primary keys (eventually empty)
   */
  public Collection ejbFindAllComments()
    throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM comments");
      ResultSet rs = stmt.executeQuery();
      int pk;
      LinkedList results = new LinkedList();
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new CommentPK(new Integer(pk)));
        }
        while (rs.next());
      }
      rs.close();
      stmt.close();
      conn.close();
      return results;
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new FinderException("Failed to get all comments: " + e);
    }
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
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public CommentPK ejbCreate(
    Integer FromUserId,
    Integer ToUserId,
    Integer ItemId,
    int Rating,
    String Comment)
    throws CreateException, RemoteException, RemoveException
  {
    // Connecting to IDManager Home interface thru JNDI
    IDManagerHome home = null;
    IDManager idManager = null;

    try
    {
      InitialContext initialContext = new InitialContext();
      home =
        (IDManagerHome) PortableRemoteObject.narrow(
          initialContext.lookup("java:comp/env/ejb/IDManager"),
          IDManagerHome.class);
    }
    catch (Exception e)
    {
      throw new EJBException("Cannot lookup IDManager: " + e);
    }
    try
    {
      IDManagerPK idPK = new IDManagerPK();
      idManager = home.findByPrimaryKey(idPK);
      id = idManager.getNextCommentID();
      fromUserId = FromUserId;
      toUserId = ToUserId;
      itemId = ItemId;
      rating = Rating;
      date = TimeManagement.currentDateToString();
      comment = Comment;
    }
    catch (Exception e)
    {
      throw new CreateException("Cannot create id for comment: " + e);
    }
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement(
          "INSERT INTO comments VALUES ("
            + id.intValue()
            + ", \""
            + fromUserId.intValue()
            + "\", \""
            + toUserId.intValue()
            + "\", \""
            + itemId.intValue()
            + "\", \""
            + rating
            + "\", \""
            + date
            + "\",\""
            + comment
            + "\")");
      stmt.executeUpdate();
      stmt.close();
      conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new CreateException("Failed to create object comment: " + e);
    }
    return new CommentPK(id);
  }

  /** This method does currently nothing */
  public void ejbPostCreate(
    Integer FromUserId,
    Integer ToUserId,
    Integer ItemId,
    int Rating,
    String Comment)
  {
  }

  /** Mandatory methods */
  public void ejbActivate() throws RemoteException
  {
  }
  public void ejbPassivate() throws RemoteException
  {
  }

  /**
   * This method delete the record from the database.
   */
  public void ejbRemove() throws RemoteException, RemoveException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("DELETE FROM comments WHERE id=?");
      stmt.setInt(1, id.intValue());
      stmt.executeUpdate();
      stmt.close();
      conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoveException("Failed to remove object comment: " + e);
    }

  }

  /** 
   * Update the record.
   */
  public void ejbStore() throws RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    if (isDirty)
    {
      isDirty = false;
      try
      {
        conn = getConnection();
        stmt =
          conn.prepareStatement(
            "UPDATE comments SET from_user_id=?, to_user_id=?, item_id=?, rating=?, date=?, comment=? WHERE id=?");
        stmt.setInt(1, fromUserId.intValue());
        stmt.setInt(2, toUserId.intValue());
        stmt.setInt(3, itemId.intValue());
        stmt.setInt(4, rating);
        stmt.setString(5, date);
        stmt.setString(6, comment);
        stmt.setInt(7, id.intValue());
        stmt.executeUpdate();
        stmt.close();
        conn.close();
      }
      catch (Exception e)
      {
        try
        {
          if (stmt != null)
            stmt.close();
          if (conn != null)
            conn.close();
        }
        catch (Exception ignore)
        {
        }
        throw new RemoteException(
          "Failed to update the record for comment: " + e);
      }
    }
  }

  /** 
   * Read the reccord from the database and update the bean.
   */
  public void ejbLoad() throws RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      CommentPK pk = (CommentPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT * FROM comments WHERE id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new EJBException("Object comment not found");
      }
      fromUserId = new Integer(rs.getInt("from_user_id"));
      toUserId = new Integer(rs.getInt("to_user_id"));
      itemId = new Integer(rs.getInt("item_id"));
      rating = rs.getInt("rating");
      date = rs.getString("date");
      comment = rs.getString("comment");
      rs.close();
      stmt.close();
      conn.close();
    }
    catch (Exception e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to update comment bean: " + e);
    }
  }

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
   * @exception RemoteException - This exception is defined in the method signature
   *                           to provide backward compatibility for enterprise beans
   *                           written for the EJB 1.0 specification. 
   *                           Enterprise beans written for the EJB 1.1 and 
   *                           higher specification should throw the javax.ejb.EJBException 
   *                           instead of this exception. 
   */
  public void setEntityContext(EntityContext context) throws RemoteException
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
   * @exception RemoteException - This exception is defined in the method signature
   *                           to provide backward compatibility for enterprise beans
   *                           written for the EJB 1.0 specification. 
   *                           Enterprise beans written for the EJB 1.1 and 
   *                           higher specification should throw the javax.ejb.EJBException 
   *                           instead of this exception.
   */
  public void unsetEntityContext() throws RemoteException
  {
    entityContext = null;
  }

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printComment(String userName) throws RemoteException
  {
    return "<DT><b><BIG><a href=\""
      + BeanConfig.context
      + "/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="
      + fromUserId
      + "\">"
      + userName
      + "</a></BIG></b>"
      + " wrote the "
      + date
      + "<DD><i>"
      + comment
      + "</i><p>\n";
  }
}
