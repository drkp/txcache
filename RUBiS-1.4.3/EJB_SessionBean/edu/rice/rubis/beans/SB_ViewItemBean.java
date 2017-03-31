package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.CreateException;
import javax.ejb.RemoveException;
import javax.ejb.EJBException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.Serializable;
import javax.transaction.UserTransaction;
import java.net.URLEncoder;

/**
 * This is a stateless session bean used to get the information about an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class SB_ViewItemBean implements SessionBean
{
  protected SessionContext sessionContext;
  protected Context initialContext = null;
  protected DataSource dataSource = null;
  private UserTransaction utx = null;

  /**
   * Get the full description of an item and the bidding option if userId>0.
   *
   * @param item an <code>Item</code> value
   * @param userId an authenticated user id
   */
  public String getItemDescription(Integer itemId, int userId)
    throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String itemName = null,
      endDate = null,
      startDate = null,
      description = null,
      sellerName = null;
    float maxBid = 0, initialPrice = 0, buyNow = 0, reservePrice = 0;
    int qty = 0, sellerId = -1, nbOfBids = 0;
    String firstBid = null;

    try
    {
      conn = dataSource.getConnection();
      conn.setAutoCommit(false);
      stmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
    }
    catch (SQLException e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to get the item: " + e);
    }
    try
    {
      if (!rs.first())
      {
        stmt.close();
        stmt = conn.prepareStatement("SELECT * FROM old_items WHERE id=?");
        stmt.setInt(1, itemId.intValue());
        rs = stmt.executeQuery();
      }
    }
    catch (SQLException e)
    {
      try
      {
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to get the item from old items: " + e);
    }
    try
    {
      if (rs.first())
      {
        itemName = rs.getString("name");
        description = rs.getString("description");
        endDate = rs.getString("end_date");
        startDate = rs.getString("start_date");
        initialPrice = rs.getFloat("initial_price");
        reservePrice = rs.getFloat("reserve_price");
        qty = rs.getInt("quantity");
        sellerId = rs.getInt("seller");

        maxBid = rs.getFloat("max_bid"); // current price
        nbOfBids = rs.getInt("nb_of_bids");

        PreparedStatement sellerStmt = null;
        ResultSet sellerResult = null;
        try
        {
          sellerStmt =
            conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
          sellerStmt.setInt(1, sellerId);
          sellerResult = sellerStmt.executeQuery();
          // Get the seller's name		 
          if (sellerResult.first())
            sellerName = sellerResult.getString("nickname");
          sellerStmt.close(); // close statement
        }
        catch (SQLException e)
        {
          try
          {
            if (sellerStmt != null)
              sellerStmt.close(); // close statement
            if (conn != null)
              conn.close();
          }
          catch (Exception ignore)
          {
          }
          throw new RemoteException("Failed to execute Query for seller: " + e);
        }
      }

      if (maxBid == 0)
      {
        firstBid = "none"; // current bid
        maxBid = initialPrice; // current price
        buyNow = rs.getFloat("buy_now");
      }
      else
      {
        // current bid = current price for the specified quantity
        if (qty > 1)
        {
          PreparedStatement bidStmt = null;
          ResultSet bidResult = null;
          try
          {
            /* Get the qty max first bids and parse bids in this order
               until qty is reached. The bid that reaches qty is the
               current minimum bid. */
            bidStmt =
              conn.prepareStatement(
                "SELECT bids.id, bids.qty, bids.bid FROM bids WHERE item_id=? ORDER BY bid DESC LIMIT ?");
            bidStmt.setInt(1, itemId.intValue());
            bidStmt.setInt(2, qty);
            bidResult = bidStmt.executeQuery();
            
          }
          catch (SQLException e)
          {
            try
            {
              if (bidStmt != null)
                bidStmt.close(); // close statement
              if (conn != null)
                conn.close();
            }
            catch (Exception ignore)
            {
            }
          }

          try
          {
            float bidValue;
            int numberOfItems = 0;
            while (bidResult.next())
            {
              bidValue = bidResult.getFloat("bid");
              numberOfItems += bidResult.getInt("qty");
              if (numberOfItems >= qty)
              {
                maxBid = bidValue;
                break;
              }
            }
            bidStmt.close(); // close statement
          }
          catch (Exception e)
          {
            try
            {
              if (stmt != null)
                stmt.close();
              if (conn != null)
                conn.close();
            }
            catch (Exception ignore)
            {
            }
            throw new RemoteException(
              "Problem while computing current bid: " + e + "<br>");
          }
        }
        Float foo = new Float(maxBid);
        firstBid = foo.toString();
      }
      if (stmt != null)
        stmt.close();
      if (conn != null)
        conn.close();

      if (userId > 0)
      {
        html.append(
          printHTMLHighlighted("You are ready to bid on: " + itemName));
      }
      else
      {
        html.append(printHTMLHighlighted(itemName));
      }
      html.append(
        "<TABLE>\n"
          + "<TR><TD>Currently<TD><b><BIG>"
          + maxBid
          + "</BIG></b>\n");
      // Check if the reservePrice has been met (if any)
      if (reservePrice > 0)
      { // Has the reserve price been met ?
        if (maxBid >= reservePrice)
          html.append("(The reserve price has been met)\n");
        else
          html.append("(The reserve price has NOT been met)\n");
      }
      html.append(
        "<TR><TD>Quantity<TD><b><BIG>"
          + qty
          + "</BIG></b>\n"
          + "<TR><TD>First bid<TD><b><BIG>"
          + firstBid
          + "</BIG></b>\n"
          + "<TR><TD># of bids<TD><b><BIG>"
          + nbOfBids
          + "</BIG></b> (<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewBidHistory?itemId="
          + itemId
          + "\">bid history</a>)\n"
          + "<TR><TD>Seller<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="
          + sellerId
          + "\">"
          + sellerName
          + "</a> (<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutCommentAuth?to="
          + sellerId
          + "&itemId="
          + itemId
          + "\">Leave a comment on this user</a>)\n"
          + "<TR><TD>Started<TD>"
          + startDate
          + "\n"
          + "<TR><TD>Ends<TD>"
          + endDate
          + "\n"
          + "</TABLE>");
      // Can the user buy this item now ?
      if (buyNow > 0)
        html.append(
          "<p><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.BuyNowAuth?itemId="
            + itemId
            + "\">"
            + "<IMG SRC=\""+BeanConfig.context+"/buy_it_now.jpg\" height=22 width=150></a>"
            + "  <BIG><b>You can buy this item right now for only $"
            + buyNow
            + "</b></BIG><br><p>\n");

      if (userId <= 0)
      {
        html.append(
          "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutBidAuth?itemId="
            + itemId
            + "\"><IMG SRC=\""+BeanConfig.context+"/bid_now.jpg\" height=22 width=90> on this item</a>\n");
      }

      html.append(printHTMLHighlighted("Item description"));
      html.append(description);
      html.append("<br><p>\n");

      if (userId > 0)
      {
        html.append(printHTMLHighlighted("Bidding"));
        float minBid = maxBid + 1;
        html.append(
          "<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreBid\" method=POST>\n"
            + "<input type=hidden name=minBid value="
            + minBid
            + ">\n"
            + "<input type=hidden name=userId value="
            + userId
            + ">\n"
            + "<input type=hidden name=itemId value="
            + itemId
            + ">\n"
            + "<input type=hidden name=maxQty value="
            + qty
            + ">\n"
            + "<center><table>\n"
            + "<tr><td>Your bid (minimum bid is "
            + minBid
            + "):</td>\n"
            + "<td><input type=text size=10 name=bid></td></tr>\n"
            + "<tr><td>Your maximum bid:</td>\n"
            + "<td><input type=text size=10 name=maxBid></td></tr>\n");
        if (qty > 1)
          html.append(
            "<tr><td>Quantity:</td>\n"
              + "<td><input type=text size=5 name=qty></td></tr>\n");
        else
          html.append("<input type=hidden name=qty value=1>\n");
        html.append(
          "</table><p><input type=submit value=\"Bid now!\"></center><p>\n");
      }
    }
    catch (Exception e)
    {
      throw new RemoteException(
        "Unable to print Item description (exception: " + e + ")<br>\n");
    }
    return html.toString();
  }

  /**
   * Construct a html highlighted string.
   * @param msg the message to display
   * @return a string in html format
   * @since 1.1
   */
  public String printHTMLHighlighted(String msg)
  {
    return "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>"
      + msg
      + "</B></FONT></TD></TR>\n</TABLE><p>\n";
  }

  // ======================== EJB related methods ============================

  /**
   * This method is empty for a stateless session bean
   */
  public void ejbCreate() throws CreateException, RemoteException
  {
  }

  /** This method is empty for a stateless session bean */
  public void ejbActivate() throws RemoteException
  {
  }
  /** This method is empty for a stateless session bean */
  public void ejbPassivate() throws RemoteException
  {
  }
  /** This method is empty for a stateless session bean */
  public void ejbRemove() throws RemoteException
  {
  }

  /** 
   * Sets the associated session context. The container calls this method 
   * after the instance creation. This method is called with no transaction context. 
   * We also retrieve the Home interfaces of all RUBiS's beans.
   *
   * @param sessionContext - A SessionContext interface for the instance. 
   * @exception RemoteException - Thrown if the instance could not perform the function 
   *            requested by the container because of a system-level error. 
   */
  public void setSessionContext(SessionContext sessionContext)
    throws RemoteException
  {
    this.sessionContext = sessionContext;
    if (dataSource == null)
    {
      // Finds DataSource from JNDI

      try
      {
        initialContext = new InitialContext();
        dataSource =
          (DataSource) initialContext.lookup("java:comp/env/jdbc/rubis");
      }
      catch (Exception e)
      {
        throw new RemoteException("Cannot get JNDI InitialContext");
      }
    }
  }

}
