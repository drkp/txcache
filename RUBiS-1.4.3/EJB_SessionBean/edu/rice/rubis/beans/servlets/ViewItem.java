package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.Enumeration;

/** This servlets displays the full description of a given item
 * and allows the user to bid on this item.
 * It must be called this way :
 * <pre>
 * http://..../ViewItem?itemId=xx where xx is the id of the item
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */


public class ViewItem extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: View item");
    sp.printHTML("<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /**
   * Display all available information on an item.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "ViewItem");
    
    String value = request.getParameter("itemId");
    if ((value == null) || (value.equals("")))
    {
      printError("No item identifier received - Cannot process the request<br>", sp);
      return ;
    }
    sp.printHTMLheader("RUBiS: Viewing Item \n");
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

    SB_ViewItemHome viewItemHome = null;
    SB_ViewItem viewItem = null;
    String html;
    try 
    {
      viewItemHome = (SB_ViewItemHome)PortableRemoteObject.narrow(initialContext.lookup("SB_ViewItemHome"),
                                                       SB_ViewItemHome.class);
      viewItem = viewItemHome.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_ViewItem: " +e+"<br>", sp);
      return ;
    }
    try
    {
      Integer itemId = new Integer(value);
      html = viewItem.getItemDescription(itemId, -1);
      sp.printHTML(html);
    }
    catch (Exception e)
    {
      printError("Cannot get item description (got exception: " +e+")<br>", sp);
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
