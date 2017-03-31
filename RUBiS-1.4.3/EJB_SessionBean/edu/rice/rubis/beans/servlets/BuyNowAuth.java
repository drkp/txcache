package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/** This servlets display the page authentifying the user 
 * to allow him to put a buy an item at the "buy now" price.
 * It must be called this way :
 * <pre>
 * http://..../BuyNowAuth?itemId=xx where xx is the id of the item
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */


public class BuyNowAuth extends HttpServlet
{
  

  /**
   * Display the web page with the form to authenticate the user
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "BuyNowAuth");
    
    String value = request.getParameter("itemId");
    if ((value == null) || (value.equals("")))
    {
      sp.printHTMLheader("RUBiS ERROR: Authentification for buying an item");
      sp.printHTML("No item identifier received - Cannot process the request<br>");
      sp.printHTMLfooter();
      return ;
    }

    sp.printHTMLheader("RUBiS: User authentification for buying an item");
    sp.printFile(Config.HTMLFilesPath+"/buy_now_auth_header.html");
    sp.printHTML("<input type=hidden name=\"itemId\" value=\""+value+"\">");
    sp.printFile(Config.HTMLFilesPath+"/auth_footer.html");
    sp.printHTMLfooter();
  }

  /**
   * Call the doGet method
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
