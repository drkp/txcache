package edu.rice.rubis.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** This servlets display the page authentifying the user 
 * to allow him to put a comment on another user.
 * It must be called this way :
 * <pre>
 * http://..../PutCommentAuth?to=xx&itemId=yy
 *     where xx is the id of the user that will receive the comment
 *           yy is the item id to which this comment is related to
 * /<pre>
 */

public class PutCommentAuth extends HttpServlet
{


  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "PubCommentAuth");

    String to = request.getParameter("to");
    String item = request.getParameter("itemId");
    if ((to == null) || (to.equals("")) || (item == null) || (item.equals("")))
    {
      sp.printHTMLheader("RUBiS ERROR: Authentification for comment");
      sp.printHTML(
        "No item or user identifier received - Cannot process the request<br>");
      sp.printHTMLfooter();
      return;
    }

    sp.printHTMLheader("RUBiS: User authentification for comment");
    sp.printFile(Config.HTMLFilesPath + "/put_comment_auth_header.html");
    sp.printHTML("<input type=hidden name=\"to\" value=\"" + to + "\">");
    sp.printHTML("<input type=hidden name=\"itemId\" value=\"" + item + "\">");
    sp.printFile(Config.HTMLFilesPath + "/auth_footer.html");
    sp.printHTMLfooter();
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    doGet(request, response);
  }
}
