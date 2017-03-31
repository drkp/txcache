package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.GregorianCalendar;

/** This servlets display the page allowing a user to buy an item
 * It must be called this way :
 * <pre>
 * http://..../BuyNow?itemId=xx&nickname=yy&password=zz
 *    where xx is the id of the item
 *          yy is the nick name of the user
 *          zz is the user password
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */


public class BuyNow extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Buy Now");
    sp.printHTML("<h2>Your request has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }


  /**
   * Authenticate the user and end the display a buy now form
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
    sp = new ServletPrinter(response, "BuyNow");
    
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

    SB_BuyNowHome buyHome;
    SB_BuyNow buy;
     try 
    {
      buyHome = (SB_BuyNowHome)PortableRemoteObject.narrow(initialContext.lookup("SB_BuyNowHome"),
                                                     SB_BuyNowHome.class);
      buy = buyHome.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_BuyNow: " +e+"<br>", sp);
      return ;
    }

     Integer itemId = new Integer(itemStr);
     String html;
    try 
    {
      sp.printHTMLheader("RUBiS: Buy now");
      html = buy.getBuyNowForm(itemId, name, pass);
      sp.printHTML(html);
      sp.printHTMLfooter();
    }
    catch (Exception e)
    {
      printError("Cannot get Buy Now form: " +e+"<br>", sp);
    } 
   
  }

  /**
   * Call the doGet method
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
