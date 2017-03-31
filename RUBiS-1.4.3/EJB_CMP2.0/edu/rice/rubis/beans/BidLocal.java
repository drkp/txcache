package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Local Interface for the Bid Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface BidLocal extends EJBLocalObject {
  /**
   * Get bid's id.
   *
   * @return bid id
   */
  public Integer getId();

  /**
   * Get the user id which is the primary key in the users table.
   *
   * @return user id
   */
  public Integer getUserId();

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   */
  public Integer getItemId();

  /**
   * Get how many of this item the user wants.
   *
   * @return quantity of items for this bid.
   */
  public int getQuantity();

  /**
   * Get the bid of the user.
   *
   * @return user's bid
   */
  public float getBid();

  /**
   * Get the maximum bid wanted by the user.
   *
   * @return user's maximum bid
   */
  public float getMaxBid();

  /**
   * Time of the Bid in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return bid time
   */
  public String getDate();

  /**
   * Give the nick name of the bidder.
   *
   * @return bidder's nick name
   */
  public String getBidderNickName();
  

  /**
   * Set a new user identifier. This id must match
   * the primary key of the users table.
   *
   * @param id user id
   */
  public void setUserId(Integer id);

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   */
  public void setItemId(Integer id);

  /**
   * Set a new quantity for this bid
   *
   * @param Qty quantity
   */
  public void setQuantity(int Qty);

  /**
   * Set a new bid on the item for the user
   *
   * @param newBid bid price
   */
  public void setBid(float newBid);

  /**
   * Set a new maximum bid on the item for the user
   *
   * @param newBid maximum bid price
   */
  public void setMaxBid(float newBid);

  /**
   * Set a new date for this bid
   *
   * @param newDate bid date
   */
  public void setDate(String newDate);

  /**
   * Display bid history information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printBidHistory();
}
