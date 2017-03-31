package edu.rice.rubis.beans.servlets;

import edu.rice.rubis.beans.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Add a new item in the database
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class RegisterItem extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Register user");
    sp.printHTML("<h2>Your registration has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /**
   * Check the values from the html register item form and create a new item
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    ServletPrinter sp = null;
    
    String  name=null, description=null;
    float   initialPrice, buyNow, reservePrice;
    Float   stringToFloat;
    int     quantity, duration;
    Integer categoryId, userId, stringToInt;
    String  startDate, endDate;
    int     itemId;

    sp = new ServletPrinter(response, "RegisterItem");
      
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

    String value = request.getParameter("name");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a name!<br>", sp);
      return ;
    }
    else
      name = value;

    value = request.getParameter("description");
    if ((value == null) || (value.equals("")))
    {
      description="No description";
    }
    else
      description = value;

    value = request.getParameter("initialPrice");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide an initial price!<br>", sp);
      return ;
    }
    else
    {
      stringToFloat = new Float(value);
      initialPrice = stringToFloat.floatValue();
    }

    value = request.getParameter("reservePrice");
    if ((value == null) || (value.equals("")))
    {
      reservePrice = 0;
    }
    else
    {
      stringToFloat = new Float(value);
      reservePrice = stringToFloat.floatValue();

    }

    value = request.getParameter("buyNow");
    if ((value == null) || (value.equals("")))
    {
      buyNow = 0;
    }
    else
    {
      stringToFloat = new Float(value);
      buyNow = stringToFloat.floatValue();
    }
 
    value = request.getParameter("duration");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a duration!<br>", sp);
      return ;
    }
    else
    {
      stringToInt = new Integer(value);
      duration = stringToInt.intValue();
      GregorianCalendar now, later;
      now = new GregorianCalendar();
      later = TimeManagement.addDays(now, duration);
      startDate = TimeManagement.dateToString(now);
      endDate = TimeManagement.dateToString(later);
    }

    value = request.getParameter("quantity");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a quantity!<br>", sp);
      return ;
    }
    else
    {
      stringToInt = new Integer(value);
      quantity = stringToInt.intValue();
    }
 
    userId = new Integer(request.getParameter("userId"));
    categoryId = new Integer(request.getParameter("categoryId"));

    // Try to create a new item
    SB_RegisterItemHome regHome;
    SB_RegisterItem reg;
    try 
    {
      regHome = (SB_RegisterItemHome)PortableRemoteObject.narrow(initialContext.lookup("SB_RegisterItemHome"),
                                                     SB_RegisterItemHome.class);
     reg = regHome.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_RegisterItem: " +e+"<br>", sp);
      return ;
    }

    try
    {
      String html;
      sp.printHTMLheader("RUBiS: Selling "+name);
      html = reg.createItem(name, description, initialPrice, quantity, reservePrice, buyNow, startDate, endDate, userId.intValue(), categoryId.intValue());

      sp.printHTML("<center><h2>Your Item has been successfully registered.</h2></center><br>\n");
      sp.printHTML("<b>RUBiS has stored the following information about your item:</b><br><p>\n");
      sp.printHTML("<TABLE>\n");
      sp.printHTML("<TR><TD>Name<TD>"+name+"\n");
      sp.printHTML("<TR><TD>Description<TD>"+description+"\n");
      sp.printHTML("<TR><TD>Initial price<TD>"+initialPrice+"\n");
      sp.printHTML("<TR><TD>ReservePrice<TD>"+reservePrice+"\n");
      sp.printHTML("<TR><TD>Buy Now<TD>"+buyNow+"\n");
      sp.printHTML("<TR><TD>Quantity<TD>"+quantity+"\n");
      sp.printHTML("<TR><TD>Duration<TD>"+duration+"\n"); 
      sp.printHTML("</TABLE>\n");
      sp.printHTML("<br><b>The following information has been automatically generated by RUBiS:</b><br>\n");
      sp.printHTML("<TABLE>\n");
      sp.printHTML("<TR><TD>Start date<TD>"+startDate+"\n");
      sp.printHTML("<TR><TD>end date<TD>"+endDate+"\n");
      sp.printHTML("<TR><TD>User id<TD>"+userId+"\n");
      sp.printHTML("<TR><TD>Category id<TD>"+categoryId+"\n");
      sp.printHTML(html);
      sp.printHTML("</TABLE>\n");
      sp.printHTMLfooter();
    }
    catch (Exception e)
    {
      printError("RUBiS internal error: Item registration failed (got exception: " +e+")<br>", sp);
      return ;
    }
   
  }
    
 
  /**
   * Call the doGet method: check the values from the html register item form 
   *	and create a new item
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
