package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;
import java.sql.Connection;

/**
 * This is the Remote Interface of the SB_AboutMe Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_AboutMe extends EJBObject, Remote {

   /**
   * Authenticate the user and get the information about the user.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getAboutMe(String username, String password) throws RemoteException;

  /** List items the user is currently selling and sold in the past 30 days */
  public String listItem(Integer userId, Connection conn) throws RemoteException;

  /** List items the user bought in the last 30 days*/
  public String listBoughtItems(Integer userId, Connection conn) throws RemoteException;

  /** List items the user won in the last 30 days*/
  public String listWonItems(Integer userId, Connection conn) throws RemoteException;

 /** List comments about the user */
  public String listComments(Integer userId, Connection conn) throws RemoteException;

  /** List items the user put a bid on in the last 30 days*/
  public String listBids(Integer userId, String username, String password, Connection conn) throws RemoteException;

  /** 
   * user's bought items list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printUserBoughtItemHeader() throws RemoteException;

  /** 
   * user's won items list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printUserWonItemHeader() throws RemoteException;

  /** 
   * user's bids list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printUserBidsHeader() throws RemoteException;

  /** 
   * user's sellings header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
 public String printSellHeader(String title) throws RemoteException;

  /** 
   * Item footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
 public String printItemFooter() throws RemoteException;

  /**
   * Construct a html highlighted string.
   * @param msg the message to display
   * @return a string in html format
   * @since 1.1
   */
  public String printHTMLHighlighted(String msg) throws RemoteException;

  /** 
   * Comment header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentHeader() throws RemoteException;

  /** 
   * Comment footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentFooter() throws RemoteException;

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printUserBoughtItem(int id, String name, int qty, float buyNow, int sellerId, String sellerName) throws RemoteException;

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code (Warning last link must be completed by servlet)
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemUserHasBidOn(int id, String name, float initialPrice, float maxBid, float bidMaxBid, int quantity, String startDate, String endDate, int sellerId, String sellerName, String username, String password) throws RemoteException;

  /**
   * Display item information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printSell(int id, String name, float initialPrice, float maxBid, int quantity, float reservePrice, float buyNow, String startDate, String endDate) throws RemoteException;

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printUserWonItem(int id, String name, float maxBid, int sellerId, String sellerName) throws RemoteException;

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printComment(String userName, String date, String comment, int fromUserId) throws RemoteException;


}
