package edu.rice.rubis.servlets;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** This servlets displays the full description of a given item
 * and allows the user to bid on this item.
 * It must be called this way :
 * <pre>
 * http://..../ViewItem?itemId=xx where xx is the id of the item
 * /<pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class ViewItem extends RubisHttpServlet
{


  public int getPoolSize()
  {
    return Config.ViewItemPoolSize;
  }

/**
 * Close both statement and connection to the database.
 */
  private void closeConnection(PreparedStatement stmt, Connection conn)
  {
    try
    {
      if (stmt != null)
        stmt.close(); // close statement
      if (conn != null)
        releaseConnection(conn);
    }
    catch (Exception ignore)
    {
    }
  }

/**
 * Display an error message.
 * @param errorMsg the error message value
 */
  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: View item");
    sp.printHTML(
      "<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
    
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    ServletPrinter sp = null;
    PreparedStatement stmt = null;
    Connection conn = null;
    
    sp = new ServletPrinter(response, "ViewItem");
    ResultSet rs = null;

    String value = request.getParameter("itemId");
    if ((value == null) || (value.equals("")))
    {
      printError("No item identifier received - Cannot process the request<br>", sp);
      return;
    }
    Integer itemId = new Integer(value);
    // get the item
    try
    {
      conn = getConnection();
      stmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      stmt.setInt(1, itemId.intValue());
      rs = stmt.executeQuery();
    }
    catch (Exception e)
    {
      sp.printHTML("Failed to execute Query for item: " + e);
      closeConnection(stmt, conn);
      return;
    }
    /**
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
    catch (Exception e)
    {
      sp.printHTML("Failed to execute Query for item in table old_items: " + e);
      closeConnection(stmt, conn);
      return;
    }
    */
    try
    {
      if (!rs.first())
      {
        sp.printHTML("<h2>This item does not exist!</h2>");
        closeConnection(stmt, conn);
        return;
      }
      String itemName, endDate, startDate, description, sellerName;
      float maxBid, initialPrice, buyNow, reservePrice;
      int quantity, sellerId, nbOfBids = 0;
      itemName = rs.getString("name");
      description = rs.getString("description");
      endDate = rs.getString("end_date");
      startDate = rs.getString("start_date");
      initialPrice = rs.getFloat("initial_price");
      reservePrice = rs.getFloat("reserve_price");
      buyNow = rs.getFloat("buy_now");
      quantity = rs.getInt("quantity");
      sellerId = rs.getInt("seller");

      maxBid = rs.getFloat("max_bid");
      nbOfBids = rs.getInt("nb_of_bids");
      if (maxBid < initialPrice)
        maxBid = initialPrice;

      PreparedStatement sellerStmt = null;
      try
      {
        sellerStmt =
          conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
        sellerStmt.setInt(1, sellerId);
        ResultSet sellerResult = sellerStmt.executeQuery();
        // Get the seller's name		 
        if (sellerResult.first())
          sellerName = sellerResult.getString("nickname");
        else
        {
          sp.printHTML("Unknown seller");
          sellerStmt.close();
          closeConnection(stmt, conn);
          return;
        }
        sellerStmt.close();

      }
      catch (SQLException e)
      {
        sp.printHTML("Failed to executeQuery for seller: " + e);
        sellerStmt.close();
        closeConnection(stmt, conn);
        return;
      }
      sp.printItemDescription(
        itemId.intValue(),
        itemName,
        description,
        initialPrice,
        reservePrice,
        buyNow,
        quantity,
        maxBid,
        nbOfBids,
        sellerName,
        sellerId,
        startDate,
        endDate,
        -1,
        conn);
    }
    catch (Exception e)
    {
      printError("Exception getting item list: " + e + "<br>", sp);
    }
    closeConnection(stmt, conn);
    sp.printHTMLfooter();
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    doGet(request, response);
  }

  /**
  * Clean up the connection pool.
  */
  public void destroy()
  {
    super.destroy();
  }
}
