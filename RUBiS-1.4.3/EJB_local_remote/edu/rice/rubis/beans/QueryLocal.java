package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;
import java.util.Vector;

/**
 * This is the local Interface of the Query Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface QueryLocal extends EJBLocalObject {
  /**
  /** 
   * Get all the items that match a specific category and that are still
   * to sell (auction end date is not passed). You must select the starting
   * row and number of rows to fetch from the database to get only a limited 
   *number of items.
   * For example, returns 25 Books.
   *
   * @param categoryId id of the category you are looking for
   * @param regionId id of the region you are looking for
   * @param startingRow row where result starts (0 if beginning)
   * @param nbOfRows number of rows to get
   *
   * @return Vector of items primary keys
   * @since 1.1
   */
  public Vector getCurrentItemsInCategory(Integer categoryId, int startingRow, int nbOfRows);

  /** 
   * Get all the items that match a specific category and region and
   * that are still to sell (auction end date is not passed). You must
   * select the starting row and number of rows to fetch from the database
   * to get only a limited number of items.
   * For example, returns 25 Books to sell in Houston.
   *
   * @param categoryId id of the category you are looking for
   * @param regionId id of the region you are looking for
   * @param startingRow row where result starts (0 if beginning)
   * @param nbOfRows number of rows to get
   *
   * @return Vector of items primary keys
   * @since 1.1
   */
  public Vector getCurrentItemsInCategoryAndRegion(Integer categoryId, Integer regionId, int startingRow, int nbOfRows);

  /**
   * Get the maximum bid (winning bid) for an item.
   *
   * @param itemId item id
   *
   * @return maximum bid or 0 if no bid
   * @since 1.0
   */
  public float getItemMaxBid(Integer itemId);
  
  /**
   * Get the first <i>maxToCollect</i> bids for an item sorted from the
   * maximum to the minimum.
   *
   * @param maxToCollect number of bids to collect
   * @param itemId item id
   *
   * @return Vector of bids primary keys (can be less than maxToCollect)
   * @since 1.0
   */
  public Vector getItemQtyMaxBid(int maxToCollect, Integer itemId);
  
  /**
   * Get the number of bids for an item.
   *
   * @param itemId item id
   *
   * @return number of bids or 0 if no bid
   * @since 1.0
   */
  public int getItemNbOfBids(Integer itemId);
  
  /**
   * Get the bid history for an item sorted from the last bid to the
   * first bid (oldest one).
   *
   * @param itemId item id
   *
   * @return Vector of bids primary keys or null if no bids
   * @since 1.0
   */
  public Vector getItemBidHistory(Integer itemId);
  
  /**
   * Get all the latest bids for each item the user has bid on.
   *
   * @param userId user id
   *
   * @return Vector of bids primary keys (can be less than maxToCollect)
   * @since 1.0
   */
  public Vector getUserBids(Integer userId);

  /**
   * Get all the items the user won in the last 30 days.
   *
   * @param userId user id
   *
   * @return Vector of items primary keys (can be less than maxToCollect)
   * @since 1.0
   */
  public Vector getUserWonItems(Integer userId);


 


}
