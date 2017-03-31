package edu.rice.rubis.beans;

import java.rmi.RemoteException;

import javax.ejb.EJBObject;

/**
 * This is the Remote Interface of the Old Item Bean.
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface OldItem extends EJBObject
{
  /**
   * Get item id.
   *
   * @return item id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Integer getId() throws RemoteException;

  /**
   * Get item name. This description is usually a short description of the item.
   *
   * @return item name
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getName() throws RemoteException;

  /**
   * Get item description . This is usually an HTML file describing the item.
   *
   * @return item description
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getDescription() throws RemoteException;

  /**
   * Get item initial price set by the seller.
   *
   * @return item initial price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getInitialPrice() throws RemoteException;

  /**
   * Get how many of this item are to be sold.
   *
   * @return item quantity
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public int getQuantity() throws RemoteException;

  /**
   * Get item reserve price set by the seller. The seller can refuse to sell if reserve price is not reached.
   *
   * @return item reserve price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getReservePrice() throws RemoteException;

  /**
   * Get item Buy Now price set by the seller. A user can directly by the item at this price (no auction).
   *
   * @return item Buy Now price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getBuyNow() throws RemoteException;

  /**
   * Get item maximum bid (if any) for this item. This value should be the same as doing <pre>SELECT MAX(bid) FROM bids WHERE item_id=?</pre>
   *
   * @return current maximum bid or 0 if no bid
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public float getMaxBid() throws RemoteException;

  /**
   * Get number of bids for this item. This value should be the same as doing <pre>SELECT COUNT(*) FROM bids WHERE item_id=?</pre>
   *
   * @return number of bids
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public int getNbOfBids() throws RemoteException;

  /**
   * Start date of the auction in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return start date of the auction
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getStartDate() throws RemoteException;

  /**
   * End date of the auction in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return end date of the auction
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getEndDate() throws RemoteException;

  /**
   * Give the user id of the seller
   *
   * @return seller's user id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Integer getSellerId() throws RemoteException;

  /**
   * Give the category id of the item
   *
   * @return item's category id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Integer getCategoryId() throws RemoteException;

  /**
   * Get the seller's nickname by finding the Bean corresponding
   * to the user. 
   *
   * @return nickname
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getSellerNickname() throws RemoteException;

  /**
   * Get the category name by finding the Bean corresponding to the category Id.
   *
   * @return category name
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getCategoryName() throws RemoteException;

  /**
   * Set a new item identifier
   *
   * @param newId item identifier
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setId(Integer newId) throws RemoteException;

  /**
   * Set a new item name
   *
   * @param newName item name
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setName(String newName) throws RemoteException;

  /**
   * Set a new item description
   *
   * @param newDescription item description
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setDescription(String newDescription) throws RemoteException;

  /**
   * Set a new initial price for the item
   *
   * @param newInitialPrice item initial price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setInitialPrice(float newInitialPrice) throws RemoteException;

  /**
   * Set a new item quantity
   *
   * @param qty item quantity
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setQuantity(int qty) throws RemoteException;

  /**
   * Set a new reserve price for the item
   *
   * @param newReservePrice item reserve price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setReservePrice(float newReservePrice) throws RemoteException;

  /**
   * Set a new Buy Now price for the item
   *
   * @param newBuyNow item Buy Now price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setBuyNow(float newBuyNow) throws RemoteException;

  /**
   * Set item maximum bid
   *
   * @param newMaxBid new maximum bid
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public void setMaxBid(float newMaxBid) throws RemoteException;

  /**
   * Set the number of bids for this item
   *
   * @param newNbOfBids new number of bids
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public void setNbOfBids(int newNbOfBids) throws RemoteException;

  /**
   * Add one bid for this item
   *
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public void addOneBid() throws RemoteException;

  /**
   * Set a new beginning date for the auction
   *
   * @param newDate auction new beginning date
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setStartDate(String newDate) throws RemoteException;

  /**
   * Set a new ending date for the auction
   *
   * @param newDate auction new ending date
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setEndDate(String newDate) throws RemoteException;

  /**
   * Set a new seller identifier. This id must match
   * the primary key of the users table.
   *
   * @param id seller id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setSellerId(Integer id) throws RemoteException;

  /**
   * Set a new category identifier. This id must match
   * the primary key of the category table.
   *
   * @param id category id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setCategoryId(Integer id) throws RemoteException;
}
