package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.rice.rubis.beans.Item;
import edu.rice.rubis.beans.ItemHome;
import edu.rice.rubis.beans.ItemPK;

/** This servlets display the page allowing a user to put a bid
 * on an item.
 * It must be called this way :
 * <pre>
 * http://..../PutBid?itemId=xx&nickname=yy&password=zz
 *    where xx is the id of the item
 *          yy is the nick name of the user
 *          zz is the user password
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */


public class PutBid extends HttpServlet
{


  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: PutBid");
    sp.printHTML("<h2>Your request has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }


  /**
   * Describe <code>doGet</code> method here.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    String itemStr = request.getParameter("itemId");
    String name = request.getParameter("nickname");
    String pass = request.getParameter("password");
    sp = new ServletPrinter(response, "PubBid");
    
    if ((itemStr == null) || (itemStr.equals("")) ||
        (name == null) || (name.equals(""))||
        (pass == null) || (pass.equals("")))
    {
      printError("Item id, name and password are required - Cannot process the request<br>", sp);
      return ;
    }

    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

    // Authenticate the user who want to bid
    Auth auth = new Auth(initialContext, sp);
    int userId = auth.authenticate(name, pass);
    if (userId == -1)
    {
      printError(" You don't have an account on RUBiS!<br>You have to register first.<br>", sp);
      return ;	
    }

    // Try to find the Item corresponding to the Item ID
    ItemHome itemHome;
    try 
    {
      itemHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("ItemHome"),
                                                       ItemHome.class);
    } 
    catch (Exception e)
    {
      printError("Cannot lookup Item: " +e+"<br>", sp);
      return ;
    }

    try
    {
      Integer itemId = new Integer(itemStr);
      Item    item = itemHome.findByPrimaryKey(new ItemPK(itemId));

      // Display the form for bidding
      sp.printItemDescription(item, userId, initialContext);
    }
    catch (Exception e)
    {
      printError("This item does not exist (got exception: " +e+")<br>", sp);
      return ;
    }

    sp.printHTMLfooter();
  }

  /**
   * Call the <code>doGet</code> method.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    doGet(request, response);
  }
}
