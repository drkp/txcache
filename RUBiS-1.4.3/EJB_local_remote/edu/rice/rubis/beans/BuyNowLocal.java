package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Local Interface for the BuyNow Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface BuyNowLocal extends EJBLocalObject {
  /**
   * Get BuyNow id.
   *
   * @return BuyNow id
   */
  public Integer getId();

  /**
   * Get the buyer id which is the primary key in the users table.
   *
   * @return buyer id
   */
  public Integer getBuyerId();

  /**
   * Get the item id which is the primary key in the items table.
   *
   * @return item id
   */
  public Integer getItemId();

  /**
   * Get how many of this item the buyer has bought.
   *
   * @return quantity of items for this bid.
   */
  public int getQuantity();

  /**
   * Time of the BuyNow in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return bid time
   */
  public String getDate();

  /**
   * Set a new buyer identifier. This id must match
   * the primary key of the buyers table.
   *
   * @param id buyer id
   */
  public void setBuyerId(Integer id);

  /**
   * Set a new item identifier. This id must match
   * the primary key of the items table.
   *
   * @param id item id
   */
  public void setItemId(Integer id);

  /**
   * Set a new quantity for this buy
   *
   * @param Qty quantity
   */
  public void setQuantity(int Qty);

  /**
   * Set a new date for this buy
   *
   * @param newDate bid date
   */
  public void setDate(String newDate);
}
