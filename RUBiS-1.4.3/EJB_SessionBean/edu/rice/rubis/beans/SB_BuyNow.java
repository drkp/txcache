package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the SB_BuyNow Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_BuyNow extends EJBObject, Remote {

  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBuyNowForm(Integer itemId, String username, String password) throws RemoteException;

  /**
   * Display item information for the Buy Now servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemDescriptionToBuyNow(int itemId, String itemName, String description, float buyNow, int quantity, int sellerId, String sellerName, String startDate, String endDate, int userId) throws RemoteException;

}
