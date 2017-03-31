package edu.rice.rubis.beans;

import java.rmi.RemoteException;

import javax.ejb.EJBObject;

/**
 * This is the Remote Interface of the Category Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface Category extends EJBObject
{
  /**
   * Get category's id.
   *
   * @return category id
   * @exception RemoteException if an error occurs
   */
  public Integer getId() throws RemoteException;

  /**
   * Get the category name.
   *
   * @return category name
   * @exception RemoteException if an error occurs
   */
  public String getName() throws RemoteException;

  /**
   * Set category's name
   *
   * @param newName category name
   * @exception RemoteException if an error occurs
   */
  public void setName(String newName) throws RemoteException;

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategory() throws RemoteException;

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategoryByRegion(int regionId) throws RemoteException;

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printCategoryToSellItem(int userId) throws RemoteException;
}
