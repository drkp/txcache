/*
 * RUBiS
 * Copyright (C) 2002, 2003, 2004 French National Institute For Research In Computer
 * Science And Control (INRIA).
 * Contact: jmob@objectweb.org
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2.1 of the License, or any later
 * version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * Initial developer(s): Emmanuel Cecchet, Julie Marguerite
 * Contributor(s): 
 */
 package edu.rice.rubis.client;

import java.util.Vector;
import java.net.URL;

/**
 * This class provides the needed URLs to access all features of RUBiS (EJB version).
 * You must provide the name and port of the Web site running RUBiS as well
 * as the directories where the scripts and HTML files reside. For example:
 * <pre>
 * URLGenerator rubisWeb = new URLGeneratorEJB("www.testbed.cs.rice.edu", 80, "/EJB_HTML", "/servlet");
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class URLGeneratorEJB extends URLGenerator
{

  /**
   * Set the name and port of the Web site running RUBiS as well as the
   * directories where the HTML and scripts reside. Examples:
   * <pre>
   * URLGenerator rubisWeb = new URLGenerator("www.testbed.cs.rice.edu", 80, "/EJB_HTML", "/servlet");
   * </pre>
   *
   * @param host Web site address
   * @param port HTTP server port
   * @param HTMLFilesPath path where HTML files reside
   * @param ScriptFilesPath path to the script files
   */
  public URLGeneratorEJB(Vector<URL> hosts, String HTMLFilesPath, String ScriptFilesPath, String ExtraQueryString)
  {
    super(hosts, HTMLFilesPath, ScriptFilesPath, ExtraQueryString);
  }


  /**
   * Returns the name of the About Me servlet.
   *
   * @return About Me servlet name
   */
  public String AboutMeScript()
  {
    return "edu.rice.rubis.beans.servlets.AboutMe";
  }
 

  /**
   * Returns the name of the Browse Categories servlet.
   *
   * @return Browse Categories servlet name
   */
  public String BrowseCategoriesScript()
  {
    return "edu.rice.rubis.beans.servlets.BrowseCategories";
  }

  /**
   * Returns the name of the Browse Regions servlet.
   *
   * @return Browse Regions servlet name
   */
  public String BrowseRegionsScript()
  {
    return "edu.rice.rubis.beans.servlets.BrowseRegions";
  }

  /**
   * Returns the name of the Buy Now servlet.
   *
   * @return Buy Now servlet name
   */
  public String BuyNowScript()
  {
    return "edu.rice.rubis.beans.servlets.BuyNow";
  }

  /**
   * Returns the name of the Buy Now Auth servlet.
   *
   * @return Buy Now Auth servlet name
   */
  public String BuyNowAuthScript()
  {
    return "edu.rice.rubis.beans.servlets.BuyNowAuth";
  }

  /**
   * Returns the name of the Put Bid servlet.
   *
   * @return Put Bid servlet name
   */
  public String PutBidScript()
  {
    return "edu.rice.rubis.beans.servlets.PutBid";
  }

  /**
   * Returns the name of the Put Bid Auth servlet.
   *
   * @return Put Bid Auth servlet name
   */
  public String PutBidAuthScript()
  {
    return "edu.rice.rubis.beans.servlets.PutBidAuth";
  }

  /**
   * Returns the name of the Put Comment servlet.
   *
   * @return Put Comment servlet name
   */
  public String PutCommentScript()
  {
    return "edu.rice.rubis.beans.servlets.PutComment";
  }

  /**
   * Returns the name of the Put Comment Auth servlet.
   *
   * @return Put Comment Auth servlet name
   */
  public String PutCommentAuthScript()
  {
    return "edu.rice.rubis.beans.servlets.PutCommentAuth";
  }

  /**
   * Returns the name of the Register Item servlet.
   *
   * @return Register Item servlet name
   */
  public String RegisterItemScript()
  {
    return "edu.rice.rubis.beans.servlets.RegisterItem";
  }

  /**
   * Returns the name of the Register User servlet.
   *
   * @return Register User servlet name
   */
  public String RegisterUserScript()
  {
    return "edu.rice.rubis.beans.servlets.RegisterUser";
  }

  /**
   * Returns the name of the Search Items By Category servlet.
   *
   * @return Search Items By Category servlet name
   */
  public String SearchItemsByCategoryScript()
  {
    return "edu.rice.rubis.beans.servlets.SearchItemsByCategory";
  }

  /**
   * Returns the name of the Search Items By Region servlet.
   *
   * @return Search Items By Region servlet name
   */
  public String SearchItemsByRegionScript()
  {
    return "edu.rice.rubis.beans.servlets.SearchItemsByRegion";
  }

  /**
   * Returns the name of the Sell Item Form servlet.
   *
   * @return Sell Item Form servlet name
   */
  public String SellItemFormScript()
  {
    return "edu.rice.rubis.beans.servlets.SellItemForm";
  }

  /**
   * Returns the name of the Store Bid servlet.
   *
   * @return Store Bid servlet name
   */
  public String StoreBidScript()
  {
    return "edu.rice.rubis.beans.servlets.StoreBid";
  }

  /**
   * Returns the name of the Store Buy Now servlet.
   *
   * @return Store Buy Now servlet name
   */
  public String StoreBuyNowScript()
  {
    return "edu.rice.rubis.beans.servlets.StoreBuyNow";
  }

  /**
   * Returns the name of the Store Comment servlet.
   *
   * @return Store Comment servlet name
   */
  public String StoreCommentScript()
  {
    return "edu.rice.rubis.beans.servlets.StoreComment";
  }

  /**
   * Returns the name of the View Bid History servlet.
   *
   * @return View Bid History servlet name
   */
  public String ViewBidHistoryScript()
  {
    return "edu.rice.rubis.beans.servlets.ViewBidHistory";
  }

  /**
   * Returns the name of the View Item servlet.
   *
   * @return View Item servlet name
   */
  public String ViewItemScript()
  {
    return "edu.rice.rubis.beans.servlets.ViewItem";
  }

  /**
   * Returns the name of the View User Info servlet.
   *
   * @return View User Info servlet name
   */
  public String ViewUserInfoScript()
  {
    return "edu.rice.rubis.beans.servlets.ViewUserInfo";
  }
}
