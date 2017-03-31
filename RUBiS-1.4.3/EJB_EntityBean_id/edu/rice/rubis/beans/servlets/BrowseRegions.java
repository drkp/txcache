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
 * Builds the html page with the list of all region in the database
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class BrowseRegions extends HttpServlet
{
  

  private void regionList(RegionHome home, ServletPrinter sp, UserTransaction utx) 
  {
    Collection list;
    Region reg;
    try 
    {
      utx.begin();	// faster if made inside a Tx
      list = home.findAllRegions();
      Iterator it = list.iterator();
      while (it.hasNext())
      {
        reg = (Region)it.next();
        sp.printRegion(reg);
      }
      utx.commit();
    } 
    catch (Exception e) 
    {
      sp.printHTML("Exception getting region list: " + e +"<br>");
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
   * Display the list of regions
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    UserTransaction utx = null;
    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "BrowseRegions");
    sp.printHTMLheader("RUBiS: Available regions");
    sp.printHTML("<h2>Currently available regions</h2><br>");
 
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      sp.printHTML("Cannot get initial context for JNDI: " +e+"<br>");
      return ;
    }

    // We want to start transactions from client: get UserTransaction
    try
    {
      utx = (UserTransaction)initialContext.lookup(Config.UserTransaction);
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup UserTransaction: "+e+"<br>");
      return ;
    }

    // Connecting to Home thru JNDI
    RegionHome home = null;
    try 
    {
      home = (RegionHome)PortableRemoteObject.narrow(initialContext.lookup("RegionHome"),
                                                     RegionHome.class);
    } 
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup Region: " +e+"<br>");
      return ;
    }

    regionList(home, sp, utx);    	

    sp.printHTMLfooter();
  }

}
