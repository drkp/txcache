package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the IDManager Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface IDManagerLocal extends EJBLocalObject {

  /**
   * Get IDManager id.
   *
   * @return IDManager id
   * @since 1.0
   */
  public Integer getId();

  /** 
   * Generate the category id.
   *
   * @return Value of the ID
   * @since 1.1
   */
    public Integer getNextCategoryID();

  /** 
   * Generate the region id.
   *
   * @return Value of the ID
   * @since 1.1
   */
    public Integer getNextRegionID();

  /** 
   * Generate the user id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextUserID();

  /** 
   * Generate the item id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextItemID();

  /** 
   * Generate the comment id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextCommentID();

  /** 
   * Generate the bid id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextBidID();

  /** 
   * Generate the buyNow id.
   *
   * @return Value of the ID
   * @since 1.1
   */
  public Integer getNextBuyNowID();

}
