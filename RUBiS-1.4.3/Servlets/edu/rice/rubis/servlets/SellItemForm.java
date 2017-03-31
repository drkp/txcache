package edu.rice.rubis.servlets;

import java.io.IOException;

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


  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    doGet(request, response);
  }

  /** Build the html page for the response */
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    String categoryId = null, userId = null;

    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "SellItemForm");
    sp.printHTMLheader("Sell your item");

    categoryId = request.getParameter("category");
    userId = request.getParameter("user");

    sp.printFile(Config.HTMLFilesPath + "/sellItemForm.html");
    sp.printHTML(
      "<input type=hidden name=\"userId\" value=\"" + userId + "\"> ");
    sp.printHTML(
      "<input type=hidden name=\"categoryId\" value=\"" + categoryId + "\"> ");
    sp.printHTMLfooter();
  }
}
