package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * IDManagerBean BMP is used to generate id since the AUTO_INCREMENT
 * feature of the database that automatically generate id on the primary key 
 * is not supported by JBoss. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class IDManagerBean implements EntityBean
{
  private EntityContext entityContext;
  private Context initialContext;
  private DataSource datasource;
  private transient boolean isDirty; // used for the isModified function

  /* Class member variables */

  public Integer id;

  public Integer categoryCount;
  public Integer regionCount;
  public Integer userCount;
  public Integer itemCount;
  public Integer commentCount;
  public Integer bidCount;
  public Integer buyNowCount;

  /**
   * Generate the category id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextCategoryID() throws RemoteException
  {
    categoryCount = new Integer(categoryCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return categoryCount;
  }

  /**
   * Generate the region id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextRegionID() throws RemoteException
  {
    regionCount = new Integer(regionCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return regionCount;
  }

  /**
   * Generate the user id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextUserID() throws RemoteException
  {
    userCount = new Integer(userCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return userCount;
  }

  /**
   * Generate the item id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextItemID() throws RemoteException
  {
    itemCount = new Integer(itemCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return itemCount;
  }

  /**
   * Generate the comment id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextCommentID() throws RemoteException
  {
    commentCount = new Integer(commentCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return commentCount;
  }

  /**
   * Generate the bid id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextBidID() throws RemoteException
  {
    bidCount = new Integer(bidCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return bidCount;
  }

  /**
   * Generate the buyNow id.
   *
   * @return Value of the ID
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public Integer getNextBuyNowID() throws RemoteException
  {
    buyNowCount = new Integer(buyNowCount.intValue() + 1);
    isDirty = true; // the bean content has been modified
    return buyNowCount;
  }

  /**
   * Retrieve a connection..
   *
   * @return connection
   * @exception Exception if an error occurs
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

  // ======================== EJB related methods ============================

  /**
   * This method is used to retrieve a IDManager Bean from its primary key,
   * that is to say its id.
   *
   * @param id IDManager id (primary key)
   *
   * @return the primary key of the IDManager if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public IDManagerPK ejbFindByPrimaryKey(IDManagerPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT category FROM ids WHERE id=?");
      stmt.setInt(1, id.getId().intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new EJBException("Object not found");
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
      throw new EJBException("Failed to retrieve object IDManager: " + e);
    }
  }

  /**
   * This method is used to create a new IDManager Bean but should never be called.
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public IDManagerPK ejbCreate()
    throws CreateException, RemoteException, RemoveException
  {
    throw new CreateException();
  }

  /** This method does currently nothing */
  public void ejbPostCreate()
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
   * This method delete a record from the database but should never be called.
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public void ejbRemove() throws RemoteException, RemoveException
  {
    throw new RemoveException();
  }

  /**
   * Update the record.
   * @exception RemoteException if an error occurs
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
            "UPDATE ids SET category=?, region=?, users=?, item=?, comment=?, bid=?, buyNow=? WHERE id=?");
        stmt.setInt(1, categoryCount.intValue());
        stmt.setInt(2, regionCount.intValue());
        stmt.setInt(3, userCount.intValue());
        stmt.setInt(4, itemCount.intValue());
        stmt.setInt(5, commentCount.intValue());
        stmt.setInt(6, bidCount.intValue());
        stmt.setInt(7, buyNowCount.intValue());
        stmt.setInt(8, id.intValue());
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
        throw new EJBException("Failed to update object idManager: " + e);
      }
    }
  }

  /**
   * Read the reccord from the database and update the bean.
   * @exception RemoteException if an error occurs
   */
  public void ejbLoad() throws RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      IDManagerPK pk = (IDManagerPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT * FROM ids WHERE id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new EJBException("Object not found");
      }
      categoryCount = new Integer(rs.getInt("category"));
      regionCount = new Integer(rs.getInt("region"));
      userCount = new Integer(rs.getInt("users"));
      itemCount = new Integer(rs.getInt("item"));
      commentCount = new Integer(rs.getInt("comment"));
      bidCount = new Integer(rs.getInt("bid"));
      buyNowCount = new Integer(rs.getInt("buyNow"));

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
      throw new EJBException("Failed to update object idManager: " + e);
    }
  }

  /**
   * Sets the associated entity context. The container invokes this method 
   *  on an instance after the instance has been created. 
   * 
   * This method is called in an unspecified transaction context. 
   * 
   * @param context - An EntityContext interface for the instance. The instance should 
   *              store the reference to the context in an instance variable. 
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

}
