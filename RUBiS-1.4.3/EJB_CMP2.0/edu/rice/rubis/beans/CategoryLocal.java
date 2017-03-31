package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;
import java.util.Collection;

/**
 * This is the local Interface of the Category Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface CategoryLocal extends EJBLocalObject {
  /**
   * Get category's id.
   *
   * @return category id
   */
  public Integer getId();

  /**
   * Get the category name.
   *
   * @return category name
   */
  public String getName();

  /**
   * Set category's name
   *
   * @param newName category name
   */
  public void setName(String newName);


  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printCategory();

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printCategoryByRegion(int regionId);

  /**
   * Display category information for the BrowseCategories servlet
   *
   * @return a <code>String</code> containing HTML code
   * @since 1.0
   */
  public String printCategoryToSellItem(int userId);

 /** 
   * Call the corresponding ejbSelect method.
   */
//  public Collection getCurrentItemsInCategory(Integer categoryId, int startingRow, int nbOfRows) throws FinderException;


 /** 
   * Call the corresponding ejbSelect method.
   */
//  public Collection getCurrentItemsInCategoryAndRegion(Integer categoryId, Integer regionId, int startingRow, int nbOfRows) throws FinderException;


}
