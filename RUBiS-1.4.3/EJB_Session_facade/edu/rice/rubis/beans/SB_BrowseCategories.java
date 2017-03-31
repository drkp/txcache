package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the SB_BrowseCategories Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_BrowseCategories extends EJBObject, Remote {

  /**
   * Get all the categories from the database.
   *
   * @return a string that is the list of categories in html format
   * @since 1.1
   */
  public String getCategories(String regionName, String userName, String password) throws RemoteException;

  /** 
   * Category related printed functions
   *
   * @param category the category to display
   * @return a string in html format
   * @since 1.1
   */

  public String printCategory(Category category) throws RemoteException;

  /** 
   * List all the categories with links to browse items by region
   * @return a string in html format
   * @since 1.1
   */
  public String printCategoryByRegion(Category category, int regionId) throws RemoteException;

  /** 
   * Lists all the categories and links to the sell item page
   * @return a string in html format
   * @since 1.1
   */
  public String printCategoryToSellItem(Category category, int userId) throws RemoteException;

}
