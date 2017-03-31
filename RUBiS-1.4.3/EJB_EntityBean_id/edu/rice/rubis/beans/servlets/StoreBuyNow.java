package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import edu.rice.rubis.beans.BuyNowHome;
import edu.rice.rubis.beans.Item;
import edu.rice.rubis.beans.ItemHome;
import edu.rice.rubis.beans.ItemPK;
import edu.rice.rubis.beans.TimeManagement;

/** This servlet records a BuyNow in the database and display
 * the result of the transaction.
 * It must be called this way :
 * <pre>
 * http://..../StoreBuyNow?itemId=aa&userId=bb&minBuyNow=cc&maxQty=dd&BuyNow=ee&maxBuyNow=ff&qty=gg 
 *   where: aa is the item id 
 *          bb is the user id
 *          cc is the minimum acceptable BuyNow for this item
 *          dd is the maximum quantity available for this item
 *          ee is the user BuyNow
 *          ff is the maximum BuyNow the user wants
 *          gg is the quantity asked by the user
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class StoreBuyNow extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: StoreBuyNow");
    sp.printHTML("<h2>Your request has not been processed due to the following error :</h2><br>");
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
   * Store the BuyNow to the database and display resulting message.
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
    Integer userId; // item id
    Integer itemId; // user id
    float   minBuyNow; // minimum acceptable BuyNow for this item
    float   BuyNow;    // user BuyNow
    float   maxBuyNow; // maximum BuyNow the user wants
    int     maxQty; // maximum quantity available for this item
    int     qty;    // quantity asked by the user

    sp = new ServletPrinter(response, "StoreBuyNow");

    /* Get and check all parameters */

    String value = request.getParameter("userId");
    if ((value == null) || (value.equals("")))
    {
      printError("<h3>You must provide a user identifier !<br></h3>", sp);
      return ;
    }
    else
      userId = new Integer(value);

    value = request.getParameter("itemId");
    if ((value == null) || (value.equals("")))
    {
      printError("<h3>You must provide an item identifier !<br></h3>", sp);
      return ;
    }
    else
      itemId = new Integer(value);


    value = request.getParameter("maxQty");
    if ((value == null) || (value.equals("")))
    {
      printError("<h3>You must provide a maximum quantity !<br></h3>", sp);
      return ;
    }
    else
    {
      Integer foo = new Integer(value);
      maxQty = foo.intValue();
    }

    value = request.getParameter("qty");
    if ((value == null) || (value.equals("")))
    {
      printError("<h3>You must provide a quantity !<br></h3>", sp);
      return ;
    }
    else
    {
      Integer foo = new Integer(value);
      qty = foo.intValue();
    }

    /* Check for invalid values */

    if (qty > maxQty)
    {
      printError("<h3>You cannot request "+qty+" items because only "+maxQty+" are proposed !<br></h3>", sp);
      return ;
    }      

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }
    // We want to start transactions from client
    UserTransaction utx = null;
    try
    {
      utx = (javax.transaction.UserTransaction)initialContext.lookup(Config.UserTransaction);
      utx.begin();	
    } 
    catch (Exception e)
    {
      printError("Cannot lookup UserTransaction: "+e+"<br>", sp);
      return ;
    }

    // Try to find the Item corresponding to the Item ID
    Item item;
    try 
    {
      ItemHome itemHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("ItemHome"),
                                                                ItemHome.class);
      item = itemHome.findByPrimaryKey(new ItemPK(itemId));
      item.setQuantity(item.getQuantity() - qty);
      if (item.getQuantity() == 0)
        item.setEndDate(TimeManagement.currentDateToString());
    } 
    catch (Exception e)
    {
      printError("Cannot update Item: " +e+"<br>", sp);
      try
      {
        utx.rollback();
      }
      catch (Exception se) 
      {
        printError("Transaction rollback failed: " + e +"<br>", sp);
      }
      return ;
    }
    try 
    {
      BuyNowHome bHome = (BuyNowHome)PortableRemoteObject.narrow(initialContext.lookup("BuyNowHome"),
                                                      BuyNowHome.class);
      edu.rice.rubis.beans.BuyNow b = bHome.create(userId, itemId, qty);
      utx.commit();
      sp.printHTMLheader("RUBiS: BuyNow result");
      if (qty == 1)
        sp.printHTML("<center><h2>Your have successfully bought this item.</h2></center>\n");
      else
        sp.printHTML("<center><h2>Your have successfully bought these items.</h2></center>\n");
    }
    catch (Exception e)
    {
      printError("Error while storing the BuyNow (got exception: " +e+")<br>", sp);
      try
      {
        utx.rollback();
      }
      catch (Exception se) 
      {
        printError("Transaction rollback failed: " + e +"<br>", sp);
      }
      return ;
    }
		
    sp.printHTMLfooter();
  }

}
