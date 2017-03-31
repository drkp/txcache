package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the SB_ViewBidHistory Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_ViewBidHistory extends EJBObject, Remote {

  /**
   * Display bid history information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printBidHistory(int userId, String bidderName, float bid, String date) throws RemoteException;

   /** 
   * Bids list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */                   
  public String printBidHistoryHeader() throws RemoteException;

 /** 
   * Bids list footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printBidHistoryFooter() throws RemoteException;

  /**
   * Get the list of bids related to a specific item.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBidHistory(Integer itemId) throws RemoteException;

 
}
