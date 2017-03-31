package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the SB_RegisterItem Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_RegisterItem extends EJBObject, Remote {

  /**
   * Create a new item.
   *
   * @param name name of the item
   * @param description item's description
   * @param initialPrice item's initial price
   * @param quantity number of items
   * @param reservePrice item's reserve price
   * @param buyNow item's price to buy it now
   * @param startDate auction's start date
   * @param endDate auction's end date
   * @param userId seller id
   * @param catagoryId category id
   * @return a string in html format
   * @since 1.1
   */
  public String createItem(String name, String description, float initialPrice, int quantity, float reservePrice, float buyNow, String startDate, String endDate, int userId, int categoryId) throws RemoteException;

}
