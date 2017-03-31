package edu.rice.rubis.beans;

import java.rmi.*;
import javax.ejb.*;
import javax.rmi.PortableRemoteObject;
import javax.naming.InitialContext;

/**
 * UserBean is an entity bean with "container managed persistence". 
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
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function

  /* Class member variables */

  public Integer id;
  public String  firstName;
  public String  lastName;
  public String  nickName;
  public String  password;
  public String  email;
  public int     rating;
  public float   balance;
  public String  creationDate;
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

    result = result+"<h2>Information about "+nickName+"<br></h2>";
    result = result+"Real life name : "+firstName+" "+lastName+"<br>";
    result = result+"Email address  : "+email+"<br>";
    result = result+"User since     : "+creationDate+"<br>";
    result = result+"Current rating : <b>"+rating+"</b><br>";
    return result;
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
  public UserPK ejbCreate(String userFirstName, String userLastName, String userNickName, String userEmail, 
                          String userPassword, Integer userRegionId) throws CreateException, RemoteException, RemoveException
  {
     // Connecting to IDManager Home interface thru JNDI
      IDManagerHome home = null;
      IDManager idManager = null;
      
      try 
      {
        InitialContext initialContext = new InitialContext();
        home = (IDManagerHome)PortableRemoteObject.narrow(initialContext.lookup(
               "java:comp/env/ejb/IDManager"), IDManagerHome.class);
      } 
      catch (Exception e)
      {
        throw new EJBException("Cannot lookup IDManager: " +e);
      }
     try 
     {
       IDManagerPK idPK = new IDManagerPK();
       idManager = home.findByPrimaryKey(idPK);
       id = idManager.getNextUserID();
       firstName = userFirstName;
       lastName  = userLastName;
       nickName  = userNickName;
       password  = userPassword;
       email     = userEmail;
       regionId  = userRegionId;
       creationDate = TimeManagement.currentDateToString();
     } 
      catch (Exception e)
      {
        throw new EJBException("Cannot create user: " +e);
      }
    return null;
  }


  /** This method just set an internal flag to 
      reload the id generated by the DB */
  public void ejbPostCreate(String userFirstName, String userLastName, String userNickName, String userEmail, 
                            String userPassword, Integer userRegionId)
  {
    isDirty = true; // the id has to be reloaded from the DB
  }

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbLoad() throws RemoteException 
  {
    isDirty = false;
  }

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbStore() throws RemoteException
  {
    isDirty = false;
  }

  /** This method is empty because persistence is managed by the container */
  public void ejbActivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbPassivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbRemove() throws RemoteException, RemoveException {}

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

}
