package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Builds the html page with the list of all categories and provides links to browse all
 * items in a category or items in a category for a given region
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class BrowseCategories extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Browse Categories");
    sp.printHTML("<h3>Your request has not been processed due to the following error :</h3><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /**
   * Build the html page for the response
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    String  region;
    String  username=null, password=null;
    Context initialContext = null;

    sp = new ServletPrinter(response, "BrowseCategories");
    sp.printHTMLheader("RUBiS available categories");
    sp.printHTML("<h2>Currently available categories</h2><br>");
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " +e+"<br>", sp);
      return ;
    }

    region = request.getParameter("region");
    username = request.getParameter("nickname");
    password = request.getParameter("password");
    

    // Connecting to Home thru JNDI
    SB_BrowseCategoriesHome home = null;
    SB_BrowseCategories sb_browseCategories = null;
    try 
    {
      home = (SB_BrowseCategoriesHome)PortableRemoteObject.narrow(initialContext.lookup("SB_BrowseCategoriesHome"),
                                                     SB_BrowseCategoriesHome.class);
      sb_browseCategories = home.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_BrowseCategories: " +e+"<br>", sp);
      return ;
    }
    String list;
    try 
    {
      list = sb_browseCategories.getCategories(region, username, password);
    } 
    catch (Exception e)
    {
      printError("Cannot get the list of categories: " +e+"<br>", sp);
      return ;
    }

    sp.printHTML(list); 	
    sp.printHTMLfooter();
  }

  /**
   * Same as <code>doGet</code>.
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
