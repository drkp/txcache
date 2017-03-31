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
   * @param description description of the item
   * @param initialPrice initial price
   * @param quantity quantity of items
   * @param reservePrice reserve price
   * @param buyNow price to buy the item without auction
   * @param duration duration of the auction
   * @param userdId seller's id
   * @param categoryId id of the category the item belong to
   * @since 1.1
   */
  public void createItem(String name, String description, float initialPrice, int quantity, float reservePrice, float buyNow, int duration, Integer userId, Integer categoryId) throws RemoteException;


}
