package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.rice.rubis.beans.SB_ViewBidHistory;
import edu.rice.rubis.beans.SB_ViewBidHistoryHome;

/** This servlets displays the list of bids regarding an item.
 * It must be called this way :
 * <pre>
 * http://..../ViewUserInfo?itemId=xx where xx is the id of the item
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class ViewBidHistory extends HttpServlet
{

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: View Bid History");
    sp.printHTML("<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /**
   * Call the <code>doPost</code> method.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    doPost(request, response);
  }

  /**
   * Display the bid history of an item
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    Context initialContext = null;
    String  value = request.getParameter("itemId");
    Integer itemId;
    
    sp = new ServletPrinter(response, "ViewBidHistory");

    if ((value == null) || (value.equals("")))
    {
      printError("You must provide an item identifier !<br>", sp);
      return ;
    }
    else
      itemId = new Integer(value);

    sp.printHTMLheader("RUBiS: Bid history");
    if(itemId.intValue() == -1)
      sp.printHTML("ItemId is -1: this item does not exist.<br>");
    else
    {
      try
      {
        initialContext = new InitialContext();
      } 
      catch (Exception e) 
      {
        printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
        return ;
      }

      SB_ViewBidHistory viewBid;
      try 
      {
        SB_ViewBidHistoryHome viewBidHome = (SB_ViewBidHistoryHome)PortableRemoteObject.narrow(initialContext.lookup("SB_ViewBidHistoryHome"),
                                                                                               SB_ViewBidHistoryHome.class);
        viewBid = viewBidHome.create();
      } 
      catch (Exception e)
      {
        printError("Cannot lookup SB_ViewBidHistory: " +e+"<br>", sp);
        return ;
      }
      
      try
      {
        sp.printHTML(viewBid.getBidHistory(itemId));
        
      }
      catch (Exception e)
      {
        sp.printHTML("Cannot get bids history (got exception: " +e+")<br>");
        sp.printHTMLfooter();
        return ;
      }
    }
    sp.printHTMLfooter();
  }

}
