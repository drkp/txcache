package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/**
 * This is the Local Home interface of the BuyNow Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface BuyNowLocalHome extends EJBLocalHome {
  /**
   * This method is used to create a new BuyNow Bean.
   * The date is automatically set to the current date when the method is called.
   *
   * @param BuyNowUserId user id of the buyer, must match the primary key of table users
   * @param BuyNowItemId item id, must match the primary key of table items
   * @param quantity number of items the user wants to buy
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   */
  public BuyNowLocal create(Integer buyNowUserId, Integer buyNowItemId, int quantity) throws CreateException;

  /**
   * This method is used to retrieve a BuyNow Bean from its primary key,
   * that is to say its id.
   *
   * @param id BuyNow id (primary key)
   *
   * @return the BuyNow if found else null
   * @exception FinderException if an error occurs
   */
  public BuyNowLocal findByPrimaryKey(BuyNowPK id) throws FinderException;

  /**
   * This method is used to retrieve all BuyNow Beans related to one item.
   * You must provide the item id.
   *
   * @param id item id
   *
   * @return List of BuyNows found (eventually empty)
   * @exception FinderException if an error occurs
   */
  public Collection findByItem(Integer id) throws FinderException;

  /**
   * This method is used to retrieve all BuyNow Beans belonging to
   * a specific user. You must provide the user id.
   *
   * @param id user id
   *
   * @return List of BuyNows found (eventually empty)
   * @exception FinderException if an error occurs
    */
  public Collection findByUser(Integer id) throws FinderException;

  /**
   * This method is used to retrieve all BuyNows from the database!
   *
   * @return List of all BuyNows (eventually empty)
   * @exception FinderException if an error occurs
   */
  public Collection findAllBuyNows() throws FinderException;

  /**
   * Get all the items the user bought using the buy-now option in the last 30 days.
   *
   * @param userId user id
   *
   * @return Vector of items primary keys (can be less than maxToCollect)
    */
//    public Collection findUserBuyNow(Integer userId) throws FinderException;

}
