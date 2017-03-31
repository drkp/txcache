package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.rice.rubis.beans.SB_PutComment;
import edu.rice.rubis.beans.SB_PutCommentHome;

/** This servlets display the page allowing a user to put a comment
 * on an item.
 * It must be called this way :
 * <pre>
 * http://..../PutComment?to=ww&itemId=xx&nickname=yy&password=zz
 *    where ww is the id of the user that will receive the comment
 *          xx is the item id
 *          yy is the nick name of the user
 *          zz is the user password
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */


public class PutComment extends HttpServlet
{

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: PutComment");
    sp.printHTML("<h2>Your request has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
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
    String toStr = request.getParameter("to");
    String itemStr = request.getParameter("itemId");
    String username = request.getParameter("nickname");
    String password = request.getParameter("password");
    sp = new ServletPrinter(response, "PubComment");
    
    if ((toStr == null) || (toStr.equals("")) ||
        (itemStr == null) || (itemStr.equals(""))||
        (username == null) || (username.equals(""))||
        (password == null) || (password.equals("")))
    {
      printError("User id, name and password are required - Cannot process the request<br>", sp);
      return ;
    }
    Integer toId = new Integer(toStr);
    if (toId.intValue() == -1)
    {
      printError("toId is -1 - Cannot process the request<br>", sp);
      return ;
    }
    Integer itemId = new Integer(itemStr);

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

    // Connecting to Home thru JNDI
    SB_PutCommentHome home = null;
    SB_PutComment sb_PutComment = null;

    try 
    {
      home = (SB_PutCommentHome)PortableRemoteObject.narrow(initialContext.lookup("SB_PutCommentHome"),
                                                     SB_PutCommentHome.class);
      sb_PutComment = home.create();
    } 
    catch (Exception e)
    {
      printError("Cannot lookup SB_PutComment: " +e+"<br>", sp);
      return ;
    }
    String html;
    // Display the comment form
    try 
    {
      sp.printHTMLheader("RUBiS: Comment service");
      html = sb_PutComment.getCommentForm(itemId, toId, username, password);
      sp.printHTML(html);
      sp.printHTML("<tr><td><b>Rating</b>\n"+
                   "<td><SELECT name=rating>\n"+
                   "<OPTION value=\"5\">Excellent</OPTION>\n"+
                   "<OPTION value=\"3\">Average</OPTION>\n"+
                   "<OPTION selected value=\"0\">Neutral</OPTION>\n"+
                   "<OPTION value=\"-3\">Below average</OPTION>\n"+
                   "<OPTION value=\"-5\">Bad</OPTION>\n"+
                   "</SELECT></table><p><br>\n"+
                   "<TEXTAREA rows=\"20\" cols=\"80\" name=\"comment\">Write your comment here</TEXTAREA><br><p>\n"+
                   "<input type=submit value=\"Post this comment now!\"></center><p>\n");
      sp.printHTMLfooter();

    } 
    catch (Exception e)
    {
      printError("Cannot get the html form: " +e+"<br>", sp);
      return ;
    }
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
