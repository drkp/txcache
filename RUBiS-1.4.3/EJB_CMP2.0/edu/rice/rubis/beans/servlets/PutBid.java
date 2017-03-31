package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.rice.rubis.beans.SB_PutBid;
import edu.rice.rubis.beans.SB_PutBidHome;

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
    Integer itemId = new Integer(itemStr);
    if (itemId.intValue() == -1)
    {
      printError( "Item id is -1.<br>", sp);
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

    SB_PutBidHome putBidHome;
    SB_PutBid putBid ;
     try 
    {
      putBidHome = (SB_PutBidHome)PortableRemoteObject.narrow(initialContext.lookup("SB_PutBidHome"),
                                                     SB_PutBidHome.class);
     putBid  = putBidHome.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_PutBidNow: " +e+"<br>", sp);
      return ;
    }
   try
    {
      // Display the form for bidding
      String html = putBid.getBiddingForm(itemId, name, pass);
      sp.printHTMLheader("RUBiS: PutBid");
      sp.printHTML(html);
      sp.printHTMLfooter();
    }
    catch (Exception e)
    {
      printError("This item does not exist (got exception: " +e+")<br>", sp);
      return ;
    }
 
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
