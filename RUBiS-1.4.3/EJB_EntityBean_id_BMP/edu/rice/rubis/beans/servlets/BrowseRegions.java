package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.Collection;
import java.util.Iterator;

/**
 * Builds the html page with the list of all region in the database
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class BrowseRegions extends HttpServlet
{
  /**
   * Get the list of regions from the database
   */
  private void regionList(RegionHome home, ServletPrinter sp) 
  {
    Collection list;
    Region reg;
    try 
    {
      list = home.findAllRegions();
      Iterator it = list.iterator();
      while (it.hasNext())
      {
        reg = (Region)it.next();
        sp.printRegion(reg);
      }
     } 
    catch (Exception e) 
    {
      sp.printHTML("Exception getting region list: " + e +"<br>");      
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

    regionList(home, sp);    	

    sp.printHTMLfooter();
  }

}
