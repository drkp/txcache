package edu.rice.rubis.beans.servlets;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Enumeration;

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
import edu.rice.rubis.beans.Query;
import edu.rice.rubis.beans.QueryHome;

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
    sp.printHTML("<h2>We cannot process your request due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
  }

  private void itemList(Integer categoryId, int page, int nbOfItems, ServletPrinter sp, Context initialContext, String categoryName)
  {
    try
    {
      Enumeration list;
      ItemPK      itemPK;
      ItemHome    iHome;
      Item        item;
      Query       query;
      QueryHome   qHome;

      qHome = (QueryHome)initialContext.lookup("QueryHome");
      //qHome = (QueryHome)PortableRemoteObject.narrow(initialContext.lookup("QueryHome"), QueryHome.class);
      query = qHome.create();
      iHome = (ItemHome)PortableRemoteObject.narrow(initialContext.lookup("ItemHome"), ItemHome.class);
      list = query.getCurrentItemsInCategory(categoryId, page*nbOfItems, nbOfItems).elements();
      if (list.hasMoreElements())
      {
        sp.printItemHeader();
        while (list.hasMoreElements()) 
        {
          itemPK = (ItemPK)list.nextElement();
          item = iHome.findByPrimaryKey(itemPK);
          sp.printItem(item);
        }
      }
      else
      {
        if (page == 0)
          sp.printHTML("<h2>Sorry, but there are no items available in this category !</h2>");
        else
        {
          sp.printHTML("<h2>Sorry, but there are no more items available in this category !</h2>");
          sp.printItemHeader();
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
    String categoryName;
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
