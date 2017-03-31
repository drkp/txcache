package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedList;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;

/**
 * UserBean is an entity bean with "bean managed persistence". 
 * The state of an instance is stored into a relational database. 
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE users (
 *    id            INTEGER UNSIGNED NOT NULL UNIQUE,
 *    firstname     VARCHAR(20),
 *    lastname      VARCHAR(20),
 *    nickname      VARCHAR(20) NOT NULL UNIQUE,
 *    password      VARCHAR(20) NOT NULL,
 *    email         VARCHAR(50) NOT NULL,
 *    rating        INTEGER,
 *    balance       FLOAT,
 *    creation_date DATETIME,
 *    region        INTEGER,
 *    PRIMARY KEY(id),
 *    INDEX auth (nickname,password),
 *    INDEX region_id (region)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class UserBean implements EntityBean
{
  protected EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function
  private Context initialContext;
  private DataSource datasource;

  /* Class member variables */

  public Integer id;
  public String firstName;
  public String lastName;
  public String nickName;
  public String password;
  public String email;
  public int rating;
  public float balance;
  public String creationDate;
  public Integer regionId;

  /**
   * Get user's id.
   *
   * @return user id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get user first name.
   *
   * @return user first name
   * @exception RemoteException if an error occurs
   */
  public String getFirstName() throws RemoteException
  {
    return firstName;
  }

  /**
   * Get user last name.
   *
   * @return user last name
   * @exception RemoteException if an error occurs
   */
  public String getLastName() throws RemoteException
  {
    return lastName;
  }

  /**
   * Get user nick name. This name is unique for each user and is used for login.
   *
   * @return user nick name
   * @exception RemoteException if an error occurs
   */
  public String getNickName() throws RemoteException
  {
    return nickName;
  }

  /**
   * Get user password.
   *
   * @return user password
   * @exception RemoteException if an error occurs
   */
  public String getPassword() throws RemoteException
  {
    return password;
  }

  /**
   * Get user email address.
   *
   * @return user email address
   * @exception RemoteException if an error occurs
   */
  public String getEmail() throws RemoteException
  {
    return email;
  }

  /**
   * Get user rating. The higher the rating is, the most reliable the user is.
   *
   * @return user rating
   * @exception RemoteException if an error occurs
   */
  public int getRating() throws RemoteException
  {
    return rating;
  }

  /**
   * Get user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each item to sell.
   *
   * @return user's account current balance
   * @exception RemoteException if an error occurs
   */
  public float getBalance() throws RemoteException
  {
    return balance;
  }

  /**
   * Get user creation date.
   *
   * @return user creation date
   * @exception RemoteException if an error occurs
   */
  public String getCreationDate() throws RemoteException
  {
    return creationDate;
  }

  /**
   * Get region identifier of user's region.
   *
   * @return region id of the user
   * @exception RemoteException if an error occurs
   */
  public Integer getRegionId() throws RemoteException
  {
    return regionId;
  }

  /**
   * Set user's first name
   *
   * @param newName user first name
   * @exception RemoteException if an error occurs
   */
  public void setFirstName(String newName) throws RemoteException
  {
    firstName = newName;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set user's last name
   *
   * @param newName user last name
   * @exception RemoteException if an error occurs
   */
  public void setLastName(String newName) throws RemoteException
  {
    lastName = newName;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set user's nick name
   *
   * @param newName user nick name
   * @exception RemoteException if an error occurs
   */
  public void setNickName(String newName) throws RemoteException
  {
    nickName = newName;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set user's password
   *
   * @param newPassword a <code>String</code> value
   * @exception RemoteException if an error occurs
   */
  public void setPassword(String newPassword) throws RemoteException
  {
    password = newPassword;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set user's email address
   *
   * @param newEmail a <code>String</code> value
   * @exception RemoteException if an error occurs
   */
  public void setEmail(String newEmail) throws RemoteException
  {
    email = newEmail;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new creation date for this user account
   *
   * @param newCreationDate a <code>String</code> value
   * @exception RemoteException if an error occurs
   */
  public void setCreationDate(String newCreationDate) throws RemoteException
  {
    creationDate = newCreationDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new region identifier. This id must match
   * the primary key of the region table.
   *
   * @param id region id
   * @exception RemoteException if an error occurs
   */
  public void setRegionId(Integer id) throws RemoteException
  {
    regionId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set user rating. The higher the rating is, the most reliable the user is.
   *
   * @param newRating new user rating
   * @exception RemoteException if an error occurs
   */
  public void setRating(int newRating) throws RemoteException
  {
    rating = newRating;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Update the current rating by adding a new value to it. This value can
   * be negative if someone wants to decrease the user rating.
   *
   * @param diff value to add to the rating
   * @exception RemoteException if an error occurs
   */
  public void updateRating(int diff) throws RemoteException
  {
    rating += diff;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each sold item.
   *
   * @param newBalance set user's account current balance
   * @exception RemoteException if an error occurs
   */
  public void setBalance(float newBalance) throws RemoteException
  {
    balance = newBalance;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Returns a string displaying general information about the user.
   * The string contains HTML tags.
   *
   * @return string containing general user information
   * @exception RemoteException if an error occurs
   */
  public String getHTMLGeneralUserInformation() throws RemoteException
  {
    String result = new String();

    result = result + "<h2>Information about " + nickName + "<br></h2>";
    result = result + "Real life name : " + firstName + " " + lastName + "<br>";
    result = result + "Email address  : " + email + "<br>";
    result = result + "User since     : " + creationDate + "<br>";
    result = result + "Current rating : <b>" + rating + "</b><br>";
    return result;
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
   * This method is used to retrieve a User Bean from its primary key,
   * that is to say its id.
   *
   * @param id User id (primary key)
   *
   * @return the user primary key if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public UserPK ejbFindByPrimaryKey(UserPK id)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM users WHERE id=?");
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
      throw new FinderException("Failed to retrieve object user: " + e);
    }
  }

  /**
   * This method is used to retrieve a User Bean from its name.
   *
   * @param nickName User nickname
   *
   * @return the primary key of the category if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public UserPK ejbFindByNickName(String nickName)
    throws FinderException, RemoteException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM users WHERE nickname=?");
      stmt.setString(1, nickName);
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new FinderException("Object user not found.");
      }
      int pk = rs.getInt("id");
      rs.close();
      stmt.close();
      conn.close();
      return new UserPK(new Integer(pk));
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
      throw new FinderException("Failed to retrieve object user: " + e);
    }
  }

  /**
   * This method is used to retrieve all users from the database!
   *
   * @return List of all users primary keys (eventually empty)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection ejbFindAllUsers() throws RemoteException, FinderException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT id FROM users");
      ResultSet rs = stmt.executeQuery();
      int pk;
      LinkedList results = new LinkedList();
      if (rs.first())
      {
        do
        {
          pk = rs.getInt("id");
          results.add(new UserPK(new Integer(pk)));
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
      throw new FinderException("Failed to get all users: " + e);
    }
  }

  // =============================== EJB methods ===================================

  /**
   * This method is used to create a new User Bean. The user id and the creationDate
   * are automatically set by the system.
   *
   * @param userFirstName user's first name
   * @param userLastName user's last name
   * @param userNickName user's nick name
   * @param userEmail email address of the user
   * @param userPassword user's password
   * @param userRegionId region id where the user lives
   *
   * @return pk primary key set to null
   *
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public UserPK ejbCreate(
    String userFirstName,
    String userLastName,
    String userNickName,
    String userEmail,
    String userPassword,
    Integer userRegionId)
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
      throw new CreateException("Cannot lookup IDManager: " + e);
    }
    try
    {
      IDManagerPK idPK = new IDManagerPK();
      idManager = home.findByPrimaryKey(idPK);
      id = idManager.getNextUserID();
      firstName = userFirstName;
      lastName = userLastName;
      nickName = userNickName;
      password = userPassword;
      email = userEmail;
      regionId = userRegionId;
      creationDate = TimeManagement.currentDateToString();
    }
    catch (Exception e)
    {
      throw new CreateException("Cannot create id for user: " + e);
    }
    PreparedStatement stmt = null;
    Connection conn = null;
    try
    {
      conn = getConnection();
      stmt =
        conn.prepareStatement(
          "INSERT INTO users VALUES ("
            + id.intValue()
            + ", \""
            + firstName
            + "\", \""
            + lastName
            + "\", \""
            + nickName
            + "\", \""
            + password
            + "\", \""
            + email
            + "\", 0, 0,\""
            + creationDate
            + "\", "
            + regionId
            + ")");
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
      throw new CreateException("Failed to create object user: " + e);
    }
    return new UserPK(id);
  }

  /** This method does currently nothing */
  public void ejbPostCreate(
    String userFirstName,
    String userLastName,
    String userNickName,
    String userEmail,
    String userPassword,
    Integer userRegionId)
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
      stmt = conn.prepareStatement("DELETE FROM users WHERE id=?");
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
      throw new RemoveException("Failed to remove object user: " + e);
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
        stmt =
          conn.prepareStatement(
            "UPDATE users SET firstname=?, lastname=?, nickname=?, password=?, email=?, rating=?, balance=?, creation_date=?, region=? WHERE id=?");
        stmt.setString(1, firstName);
        stmt.setString(2, lastName);
        stmt.setString(3, nickName);
        stmt.setString(4, password);
        stmt.setString(5, email);
        stmt.setInt(6, rating);
        stmt.setFloat(7, balance);
        stmt.setString(8, creationDate);
        stmt.setInt(9, regionId.intValue());
        stmt.setInt(10, id.intValue());
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
        throw new RemoteException("Failed to update the record for user: " + e);
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
      UserPK pk = (UserPK) entityContext.getPrimaryKey();
      id = pk.getId();
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT * FROM users WHERE id=?");
      stmt.setInt(1, id.intValue());
      ResultSet rs = stmt.executeQuery();
      if (!rs.first())
      {
        throw new RemoteException("Object user not found");
      }
      firstName = rs.getString("firstname");
      lastName = rs.getString("lastname");
      nickName = rs.getString("nickname");
      password = rs.getString("password");
      email = rs.getString("email");
      rating = rs.getInt("rating");
      balance = rs.getFloat("balance");
      creationDate = rs.getString("creation_date");
      regionId = new Integer(rs.getInt("region"));

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
      throw new RemoteException("Failed to update user bean: " + e);
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
