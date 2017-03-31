package edu.rice.rubis.beans;

import java.net.URLEncoder;
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
 * CategoryBean is an entity bean with "bean managed persistence". 
 * The state of an instance is stored into a relational database. 
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE categories (
 *    id   INTEGER UNSIGNED NOT NULL UNIQUE
 *    name VARCHAR(50),
 *    PRIMARY KEY(id)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class CategoryBean implements EntityBean
{
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function
  private Context initialContext;
  private DataSource datasource;

  /* Class member variables */

  public Integer id;
  public String name;

  /**
   * Get category's id.
   *
   * @return category id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get the category name.
   *
   * @return category name
   * @exception RemoteException if an error occurs
   */
  public String getName() throws RemoteException
  {
    return name;
  }

  /**
   * Set category's name
   *
   * @param newName category name
   * @exception RemoteException if an error occurs
   */
  public void setName(String newName) throws RemoteException
  {
    name = newName;
    isDirty = true; // the bean content has been modified
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

  /**
   * This method is used to retrieve a Category Bean from its primary key,
   * that is to say its id.
   *
   * @param id Region id (primary key)
   *
   * @return the primary key of the category if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public CategoryPK ejbFindByPrimaryKey(CategoryPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT name FROM categories WHERE id=?");
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
      throw new EJBException("Failed to retrieve object category: " + e);
    }
  }

  /**
   * This method is used to retrieve a Category Bean from its name.
   *
   * @param categoryName Category name
   *
   * @return the primary key of the category if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public CategoryPK ejbFindByName(String categoryName)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM categories WHERE name=?");
      stmt.setString(1, categoryName);
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        return null;
      }
      int pk = rs.getInt("id");
      rs.close();
      stmt.close();
      conn.close();
      return new CategoryPK(new Integer(pk));
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
      throw new EJBException("Failed to retrieve object category: " + e);
    }
  }

  /**
   * This method is used to retrieve all categories from the database!
   *
   * @return Collection of primary keys (eventually empty)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection ejbFindAllCategories()
    throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM categories");
      ResultSet rs = stmt.executeQuery();
      int pk;
      LinkedList results = new LinkedList();
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new CategoryPK(new Integer(pk)));
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
      throw new EJBException("Failed to get all categories: " + e);
    }
  }

  /**
   * This method is used to create a new Category Bean. Note that the category
   * id is automatically generated by the database (AUTO_INCREMENT) on the
   * primary key.
   *
   * @param categoryName Category name
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public CategoryPK ejbCreate(String categoryName)
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
      id = idManager.getNextCategoryID();
      name = categoryName;
    }
    catch (Exception e)
    {
      throw new EJBException("Cannot create category: " + e);
    }
    return null;
  }

  /** This method does currently nothing */
  public void ejbPostCreate(String categoryName)
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
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public void ejbRemove() throws RemoteException, RemoveException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("DELETE FROM categories WHERE id=?");
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
      throw new EJBException("Failed to remove object category: " + e);
    }

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
        stmt = conn.prepareStatement("UPDATE categories SET name=? WHERE id=?");
        stmt.setString(1, name);
        stmt.setInt(2, id.intValue());
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
        throw new EJBException("Failed to update object category: " + e);
      }
    }
  }

  /**
   * Read the reccord from the database and update the bean.
   * @exception RemoteException if an error occurs
   */
  public void ejbLoad() throws RemoteException
  {
    isDirty = false;
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      CategoryPK pk = (CategoryPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT name FROM categories WHERE id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new EJBException("Object not found");
      }
      name = rs.getString("name");
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
      throw new EJBException("Failed to update object category: " + e);
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

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategory() throws RemoteException
  {
    return "<a href=\""
      + BeanConfig.context
      + "/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="
      + id
      + "&categoryName="
      + URLEncoder.encode(name)
      + "\">"
      + name
      + "</a><br>\n";
  }

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @param regionId an <code>int</code> value
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategoryByRegion(int regionId) throws RemoteException
  {
    return "<a href=\""
      + BeanConfig.context
      + "/servlet/edu.rice.rubis.beans.servlets.SearchItemsByRegion?category="
      + id
      + "&categoryName="
      + URLEncoder.encode(name)
      + "&region="
      + regionId
      + "\">"
      + name
      + "</a><br>\n";
  }

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @param userId an <code>int</code> value
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategoryToSellItem(int userId) throws RemoteException
  {
    return "<a href=\""
      + BeanConfig.context
      + "/servlet/edu.rice.rubis.beans.servlets.SellItemForm?category="
      + id
      + "&user="
      + userId
      + "\">"
      + name
      + "</a><br>\n";
  }
}
