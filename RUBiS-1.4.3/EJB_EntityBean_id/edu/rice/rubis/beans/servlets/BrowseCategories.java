package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.transaction.UserTransaction;
import java.util.Collection;
import java.util.Iterator;

/**
 * Builds the html page with the list of all categories and provides links to browse all
 * items in a category or items in a category for a given region
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class BrowseCategories extends HttpServlet
{
  

  /** List all the categories in the database */
  private void categoryList(CategoryHome home, int regionId, int userId, ServletPrinter sp,  UserTransaction utx) 
  {
    Collection list;
    Category cat;
    try 
    {
      utx.begin();	// faster if made inside a Tx
      list = home.findAllCategories();
      if (list.isEmpty())
        sp.printHTML("<h2>Sorry, but there is no category available at this time. Database table is empty</h2><br>");
      else
      {
        sp.printHTML("<h2>Currently available categories</h2><br>");

        Iterator it = list.iterator();
        while (it.hasNext())
        {
          cat = (Category)it.next();
          if (regionId != -1)
          {
            sp.printCategoryByRegion(cat, regionId);
          }
          else
          {
            if (userId != -1)
              sp.printCategoryToSellItem(cat, userId);
            else
              sp.printCategory(cat);
          }
        }
      }
      utx.commit();
    } 
    catch (Exception e) 
    {
      sp.printHTML("Exception getting category list: " + e +"<br>");
      try
      {
        utx.rollback();
      }
      catch (Exception se) 
      {
        sp.printHTML("Transaction rollback failed: " + e +"<br>");
      }
    }
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
    UserTransaction utx = null;
    ServletPrinter sp = null;
    int     regionId = -1, userId = -1;
    String  username=null, password=null;
    Context initialContext = null;

    sp = new ServletPrinter(response, "BrowseCategories");
    sp.printHTMLheader("RUBiS available categories");

    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      sp.printHTML("Cannot get initial context for JNDI: " +e+"<br>");
      return ;
    }

    // We want to start transactions from client
    try
    {
      utx = (javax.transaction.UserTransaction)initialContext.lookup(Config.UserTransaction);
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup UserTransaction: "+e+"<br>");
      return ;
    }

    username = request.getParameter("nickname");
    password = request.getParameter("password");
    
    // Authenticate the user who wants to sell items
    if ((username != null && username !="") || (password != null && password !=""))
    {
      Auth auth = new Auth(initialContext, sp);
      userId = auth.authenticate(username, password);
      if (userId == -1)
      {
        sp.printHTML(" You don't have an account on RUBiS!<br>You have to register first.<br>");
        sp.printHTMLfooter();
        return ;	
      }
    }
    
    String value = request.getParameter("region");
    if ((value != null) && (!value.equals("")))
    {
      // Connecting to region Home interface thru JNDI
      RegionHome home = null;
      try 
      {
        home = (RegionHome)PortableRemoteObject.narrow(initialContext.lookup("RegionHome"),
                                                       RegionHome.class);
      } 
      catch (Exception e)
      {
        sp.printHTML("Cannot lookup Region: " +e+"<br>");
        sp.printHTMLfooter();
        return ;
      }
      // get the region ID
      try
      {
        Region region = home.findByName(value);
        regionId = region.getId().intValue();
      }
      catch (Exception e)
      {
        sp.printHTML(" Region "+value+" does not exist in the database!<br>(got exception: " +e+")<br>");
        sp.printHTMLfooter();
        return ;
      }
    }

    // Connecting to category Home thru JNDI
    CategoryHome home = null;
    try 
    {
      home = (CategoryHome)PortableRemoteObject.narrow(initialContext.lookup("CategoryHome"),
                                                       CategoryHome.class);
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup Category: " +e+"<br>");
      sp.printHTMLfooter();
      return ;
    }
  
    categoryList(home, regionId, userId, sp, utx);    	

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
