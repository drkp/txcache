package edu.rice.rubis.beans;

import java.rmi.*;
import javax.ejb.*;
import javax.rmi.PortableRemoteObject;
import javax.naming.InitialContext;
import java.util.Collection;

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

public abstract class UserBean implements EntityBean 
{
  private EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function

  /****************************/
  /* Abstract accessor methods*/
  /****************************/

  /**
   * Get user's id.
   *
   * @return user id
   */
  public abstract Integer getId();


  /**
   * Set user's id.
   *
   */
  public abstract void setId(Integer id);


  /**
   * Get user first name.
   *
   * @return user first name
   */
  public abstract String getFirstName();


  /**
   * Get user last name.
   *
   * @return user last name
   */
  public abstract String getLastName();


  /**
   * Get user nick name. This name is unique for each user and is used for login.
   *
   * @return user nick name
   */
  public abstract String getNickName();


  /**
   * Get user password.
   *
   * @return user password
   */
  public abstract String getPassword();


  /**
   * Get user email address.
   *
   * @return user email address
   */
  public abstract String getEmail();
 

  /**
   * Get user rating. The higher the rating is, the most reliable the user is.
   *
   * @return user rating
   */
  public abstract int getRating();

  
  /**
   * Get user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each item to sell.
   *
   * @return user's account current balance
   */
  public abstract float getBalance();


  /**
   * Get user creation date.
   *
   * @return user creation date
   */
  public abstract String getCreationDate();


  /**
   * Get region identifier of user's region.
   *
   * @return region id of the user
   */
  public abstract Integer getRegionId();



  /**
   * Set user's first name
   *
   * @param newName user first name
   */
  public abstract void setFirstName(String newName);


  /**
   * Set user's last name
   *
   * @param newName user last name
   */
  public abstract void setLastName(String newName);


  /**
   * Set user's nick name
   *
   * @param newName user nick name
   */
  public abstract void setNickName(String newName);


  /**
   * Set user's password
   *
   * @param newPassword a <code>String</code> value
   */
  public abstract void setPassword(String newPassword);


  /**
   * Set user's email address
   *
   * @param newEmail a <code>String</code> value
   */
  public abstract void setEmail(String newEmail);


  /**
   * Set a new creation date for this user account
   *
   * @param newCreationDate a <code>String</code> value
   */
  public abstract void setCreationDate(String newCreationDate);


  /**
   * Set a new region identifier. This id must match
   * the primary key of the region table.
   *
   * @param id region id
   */
  public abstract void setRegionId(Integer id);


  /**
   * Set user rating. The higher the rating is, the most reliable the user is.
   *
   * @param newRating new user rating
   */
  public abstract void setRating(int newRating);


  /**
   * Set user's account current balance. This account is used when a user want to sell items.
   * There is a charge for each sold item.
   *
   * @param newBalance set user's account current balance
   */
  public abstract void setBalance(float newBalance);


  /**
   * Update the current rating by adding a new value to it. This value can
   * be negative if someone wants to decrease the user rating.
   *
   * @param diff value to add to the rating
   */
  public void updateRating(int diff)
  {
    setRating(getRating()+diff);
    isDirty = true; // the bean content has been modified
  }
  
  /*****************/
  /* relationships */
  /*****************/

  // This entity bean has a one to many relationship with the Item entity.
//   public abstract Collection getItems();
//   public abstract void setItems(Collection items);

  // This entity bean has a one to many relationship with the bid entity.
//  public abstract Collection getBids();
//  public abstract void setBids(Collection bids);

  // This entity bean has a one to many relationship with the buyNow entity.

  /*********************/
  /* ejbSelect methods */
  /*********************/

  /**
   * Get all the items the user won in the last 30 days.
   *
   * @param userId user id
   *
   * @return Collection of items
   * @exception FinderException if an error occurs
   * @since 1.0
   */
//  public abstract Collection ejbSelectUserWonItems(Integer userId) throws FinderException;

  /**
   * Get all the maximum bids for each item the user has bid on in the last 30 days.
   *
   * @param userId user id
   *
   * @return Vector of bids primary keys (can be less than maxToCollect)
   * @exception RemoteException if an error occurs
   */
 // public abstract Collection ejbSelectUserBids(Integer userId) throws FinderException;

  /*****************/
  /* other methods */
  /*****************/

 /** 
   * Call the corresponding ejbSelect method.
   */
//  public Collection getUserWonItems(Integer userId) throws FinderException
//  {
//      return ejbSelectUserWonItems(userId);
//  }

 /** 
   * Call the corresponding ejbSelect method.
   */
//  public Collection getUserBids(Integer userId) throws FinderException
//  {
//      return ejbSelectUserBids(userId);
//  }

  /**
   * Returns a string displaying general information about the user.
   * The string contains HTML tags.
   *
   * @return string containing general user information
   */
  public String getHTMLGeneralUserInformation()
  {
    String result = new String();

    result = result+"<h2>Information about "+getNickName()+"<br></h2>";
    result = result+"Real life name : "+getFirstName()+" "+getLastName()+"<br>";
    result = result+"Email address  : "+getEmail()+"<br>";
    result = result+"User since     : "+getCreationDate()+"<br>";
    result = result+"Current rating : <b>"+getRating()+"</b><br>";
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
   */
  public UserPK ejbCreate(String userFirstName, String userLastName, String userNickName, String userEmail, 
                          String userPassword, Integer userRegionId) throws CreateException
  {
     // Connecting to IDManager Home interface thru JNDI
      IDManagerLocalHome home = null;
      IDManagerLocal idManager = null;
      
      try 
      {
        InitialContext initialContext = new InitialContext();
        home = (IDManagerLocalHome)initialContext.lookup("java:comp/env/ejb/IDManager");
      } 
      catch (Exception e)
      {
        throw new EJBException("Cannot lookup IDManager: " +e);
      }
     try 
     {
       IDManagerPK idPK = new IDManagerPK();
       idManager = home.findByPrimaryKey(idPK);
       setId(idManager.getNextUserID());
       setFirstName(userFirstName);
       setLastName(userLastName);
       setNickName(userNickName);
       setPassword(userPassword);
       setEmail(userEmail);
       setRegionId(userRegionId);
       setCreationDate(TimeManagement.currentDateToString());
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
  public void ejbActivate(){}
  /** This method is empty because persistence is managed by the container */
  public void ejbPassivate(){}
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

}
