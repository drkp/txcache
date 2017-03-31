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
 * RegionBean is an entity bean with "bean managed persistence". 
 * The state of an instance is stored into a relational database. 
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE regions (
 *    id   INTEGER UNSIGNED NOT NULL UNIQUE
 *    name VARCHAR(20),
 *    PRIMARY KEY(id)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class RegionBean implements EntityBean
{
  private EntityContext entityContext;
  private Context initialContext;
  private DataSource datasource;
  private transient boolean isDirty; // used for the isModified function

  /* Class member variables */

  public Integer id;
  public String name;

  /**
   * Get region's id.
   *
   * @return region id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get region name.
   *
   * @return region name
   * @exception RemoteException if an error occurs
   */
  public String getName() throws RemoteException
  {
    return name;
  }

  /**
   * Set region's name
   *
   * @param newName region name
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
   * This method is used to retrieve a Region Bean from its primary key,
   * that is to say its id.
   *
   * @param id Region id (primary key)
   *
   * @return the Region if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public RegionPK ejbFindByPrimaryKey(RegionPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT name FROM regions WHERE id=?");
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
      throw new EJBException("Cannot find object region: " + e);
    }
  }

  /**
   * This method is used to retrieve a Region Bean from its name.
   *
   * @param regionName Region name
   *
   * @return the Region if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public RegionPK ejbFindByName(String regionName)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM regions WHERE name=?");
      stmt.setString(1, regionName);
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        return null;
      }
      int pk = rs.getInt("id");
      rs.close();
      stmt.close();
      conn.close();
      return new RegionPK(new Integer(pk));
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
      throw new EJBException("Cannot find object region: " + e);
    }
  }

  /**
   * This method is used to retrieve all categories from the database!
   *
   * @return List of all categories (eventually empty)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection ejbFindAllRegions() throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM regions");
      ResultSet rs = stmt.executeQuery();
      int pk;
      LinkedList results = new LinkedList();
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new RegionPK(new Integer(pk)));
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
      throw new EJBException("Cannot find the list of regions: " + e);
    }
  }

  /**
   * This method is used to create a new Region Bean and insert a record in the database.
   *
   * @param regionName Region name
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public RegionPK ejbCreate(String regionName)
    throws CreateException, RemoteException, RemoveException
  {
    // Connecting to IDManager Home interface thru JNDI
    IDManagerHome home = null;
    IDManager idManager = null;

    try
    {
      initialContext = new InitialContext();
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
      id = idManager.getNextRegionID();
      name = regionName;
    }
    catch (Exception e)
    {
      throw new EJBException("Cannot create region: " + e);
    }

    return null;
  }

  /**
   * This method does currently nothing
   */
  public void ejbPostCreate(String regionName)
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
      stmt = conn.prepareStatement("DELETE FROM regions WHERE id=?");
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
      throw new EJBException("Failed to remove object region: " + e);
    }
  }

  /**
   * Update the record.
   * @exception RemoteException if an error occurs
   */
  public void ejbStore() throws RemoteException
  {
    isDirty = false;
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("UPDATE regions SET name=? WHERE id=?");
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
      throw new EJBException("Failed to update object region: " + e);
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
      RegionPK pk = (RegionPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT name FROM regions WHERE id=?");
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
      throw new EJBException("Failed to update object region: " + e);
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

}
