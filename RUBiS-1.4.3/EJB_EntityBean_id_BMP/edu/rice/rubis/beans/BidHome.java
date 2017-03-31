package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Collection;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/**
 * This is the Home interface of the Bid Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface BidHome extends EJBHome
{
  /**
   * This method is used to create a new Bid Bean.
   * The date is automatically set to the current date when the method is called.
   *
   * @param bidUserId user id of the bidder, must match the primary key of table users
   * @param bidItemId item id, must match the primary key of table items
   * @param userBid the amount of the user bid
   * @param userMaxBid the maximum amount the user wants to bid
   * @param quantity number of items the user wants to buy
   *
   * @return pk primary key set to null
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   */
  public Bid create(
    Integer bidUserId,
    Integer bidItemId,
    float userBid,
    float userMaxBid,
    int quantity)
    throws CreateException, RemoteException, RemoveException;

  /**
   * This method is used to retrieve a Bid Bean from its primary key,
   * that is to say its id.
   *
   * @param id Bid id (primary key)
   *
   * @return the Bid if found else null
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public Bid findByPrimaryKey(BidPK id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all Bid Beans related to one item.
   * You must provide the item id.
   *
   * @param id item id
   *
   * @return List of Bids found (eventually empty)
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public Collection findByItem(Integer id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all Bid Beans belonging to
   * a specific user. You must provide the user id.
   *
   * @param id user id
   *
   * @return List of Bids found (eventually empty)
   * @exception FinderException if an error occurs
   * @exception RemoteException if an error occurs
   */
  public Collection findByUser(Integer id)
    throws FinderException, RemoteException;

  /**
   * This method is used to retrieve all bids from the database!
   *
   * @return List of all bids (eventually empty)
   * @exception RemoteException if an error occurs
   * @exception FinderException if an error occurs
   */
  public Collection findAllBids() throws RemoteException, FinderException;
}
