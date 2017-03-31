package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.Enumeration;
import java.net.URLEncoder;

/** This servlets displays a list of items belonging to a specific category.
 * It must be called this way :
 * <pre>
 * http://..../SearchItemsByCategory?category=xx&categoryName=yy 
 *    where xx is the category id
 *      and yy is the category name
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class SearchItemsByCategory extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Search Items By Category");
    sp.printHTML("<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
  }

  private void itemList(Integer categoryId, int page, int nbOfItems, ServletPrinter sp, Context initialContext, String categoryName)
  {
    // Connecting to Home thru JNDI
    SB_SearchItemsByCategoryHome home = null;
    SB_SearchItemsByCategory sb_SearchItemsByCategory = null;
    String list = "";
    try 
    {
      home = (SB_SearchItemsByCategoryHome)PortableRemoteObject.narrow(initialContext.lookup("SB_SearchItemsByCategoryHome"),
                                                     SB_SearchItemsByCategoryHome.class);
      sb_SearchItemsByCategory = home.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_SearchItemsByCategory: " +e+"<br>", sp);
      return ;
    }

    try 
    {
      list = sb_SearchItemsByCategory.getItems(categoryId, page, nbOfItems);
    } 
    catch (Exception e)
    {
      printError("Cannot get the list of items: " +e+"<br>", sp);
      return ;
    }
    try
    {
      if ((list != null) && (!list.equals("")))
      {
        sp.printItemHeader();
        sp.printHTML(list); 
        sp.printItemFooter();
      }
      else
      {
        if (page == 0)
          sp.printHTML("<h2>Sorry, but there are no items available in this category !</h2>");
        else
        {
          sp.printHTML("<h2>Sorry, but there are no more items available in this category !</h2>");
          //          sp.printItemHeader();
          sp.printItemFooter("<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page-1)+"&nbOfItems="+nbOfItems+"\">Previous page</a>", "");
        }
        return ;
      }
      if (page == 0)
        sp.printItemFooter("", "<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page+1)+"&nbOfItems="+nbOfItems+"\">Next page</a>");
      else
        sp.printItemFooter("<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page-1)+"&nbOfItems="+nbOfItems+"\">Previous page</a>",
                           "<a href=\""+Config.context+"/servlet/edu.rice.rubis.beans.servlets.SearchItemsByCategory?category="+categoryId+
                           "&categoryName="+URLEncoder.encode(categoryName)+"&page="+(page+1)+"&nbOfItems="+nbOfItems+"\">Next page</a>");
    } 
    catch (Exception e) 
    {
      printError("Exception getting item list: " + e +"<br>", sp);
    }
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
    Context initialContext = null;
    String categoryName = null;
      
    String  value = request.getParameter("category");
    Integer categoryId;
    Integer page;
    Integer nbOfItems;

    categoryName = request.getParameter("categoryName");
    sp = new ServletPrinter(response, "SearchItemsByCategory");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a category identifier!<br>", sp);
      return ;
    }
    else
      categoryId = new Integer(value);

    value = request.getParameter("page");
    if ((value == null) || (value.equals("")))
      page = new Integer(0);
    else
      page = new Integer(value);

    value = request.getParameter("nbOfItems");
    if ((value == null) || (value.equals("")))
      nbOfItems = new Integer(25);
    else
      nbOfItems = new Integer(value);

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

    if (categoryName == null)
    {
      sp.printHTMLheader("RUBiS: Missing category name");
      sp.printHTML("<h2>Items in this category</h2><br><br>");
    }
    else
    {
      sp.printHTMLheader("RUBiS: Items in category "+categoryName);
      sp.printHTML("<h2>Items in category "+categoryName+"</h2><br><br>");
    }

    itemList(categoryId, page.intValue(), nbOfItems.intValue(), sp, initialContext, categoryName);
		
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
