package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Builds the html page that display the form to register a new item to sell.
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class SellItemForm extends HttpServlet
{
  


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

  /**
   * Build the html page for the response
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    String  categoryId=null, userId=null;
    Context initialContext = null;

    sp = new ServletPrinter(response, "SellItemForm");
    sp.printHTMLheader("RUBiS: Sell your item");

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      sp.printHTMLheader("RUBiS ERROR: SellItemForm");
      sp.printHTML("Cannot get initial context for JNDI: " +e+"<br>");
      sp.printHTMLfooter();
      return ;
    }

    categoryId = request.getParameter("category");
    userId = request.getParameter("user");
    if ((categoryId == null) || categoryId.equals("") || (userId == null) || userId.equals(""))
    {
      sp.printHTMLheader("RUBiS ERROR: SellItemForm");
      sp.printHTML("No category or user identifier received - Cannot process the request<br>");
      sp.printHTMLfooter();
      return ;
    }
    
    sp.printFile(Config.HTMLFilesPath+"/sellItemForm.html");
    sp.printHTML("<input type=hidden name=\"userId\" value=\""+userId+"\"> ");
    sp.printHTML("<input type=hidden name=\"categoryId\" value=\""+categoryId+"\"> ");
    sp.printHTMLfooter();
  }
}
