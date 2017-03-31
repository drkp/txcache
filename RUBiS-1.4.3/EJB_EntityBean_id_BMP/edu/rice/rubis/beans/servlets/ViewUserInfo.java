package edu.rice.rubis.beans.servlets;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import edu.rice.rubis.beans.Comment;
import edu.rice.rubis.beans.CommentHome;
import edu.rice.rubis.beans.User;
import edu.rice.rubis.beans.UserHome;
import edu.rice.rubis.beans.UserPK;

/** This servlets displays general information about a user.
 * It must be called this way :
 * <pre>
 * http://..../ViewUserInfo?userId=xx where xx is the id of the user
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class ViewUserInfo extends HttpServlet
{
  
  private void commentList(CommentHome home, Integer userId, ServletPrinter sp, Context initialContext, UserTransaction utx )
  {
    Collection list;
    Comment comment;
    UserHome uHome;
    // Retrieve UserHome to get the names of the comment authors
    try
    {
      uHome =
        (UserHome) PortableRemoteObject.narrow(
          initialContext.lookup("UserHome"),
          UserHome.class);
    }
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup users: " + e + "<br>");
      sp.printHTMLfooter();
      return;
    }

    try
    {
      utx.begin(); // faster if made inside a Tx
      list = home.findByToUser(userId);
      if (list.isEmpty())
        sp.printHTML("<h3>There is no comment yet for this user.</h3><br>");
      else
      {
        sp.printHTML("<br><hr><br><h3>Comments for this user</h3><br>");

        sp.printCommentHeader();
        // Display each comment and the name of its author
        Iterator it = list.iterator();
        while (it.hasNext())
        {
          comment = (Comment) it.next();
          String userName;
          try
          {
            User u =
              uHome.findByPrimaryKey(new UserPK(comment.getFromUserId()));
            userName = u.getNickName();
          }
          catch (Exception e)
          {
            sp.printHTML(
              "This author does not exist (got exception: " + e + ")<br>");
            try
            {
               utx.rollback();
            }
            catch (Exception se)
            {
               sp.printHTML("Transaction rollback failed: " + e + "<br>");
            }
            sp.printHTMLfooter();
            return;
          }
          sp.printComment(userName, comment);
        }
        sp.printCommentFooter();
      }
      utx.commit();
    }
    catch (Exception e)
    {
      sp.printHTML("Exception getting comment list: " + e + "<br>");
      try
      {
        utx.rollback();
      }
      catch (Exception se)
      {
        sp.printHTML("Transaction rollback failed: " + e + "<br>");
      }
    }
  }

  /**
   * Call the <code>doPost</code> method.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    doPost(request, response);
  }

  /**
   * Display information about a user.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    ServletPrinter sp = null;
    Context initialContext = null;
    UserTransaction utx = null;
    String value = request.getParameter("userId");
    Integer userId;

    sp = new ServletPrinter(response, "ViewUserInfo");

    if ((value == null) || (value.equals("")))
    {
      sp.printHTMLheader("RUBiS ERROR: View user information");
      sp.printHTML("<h3>You must provide a user identifier !<br></h3>");
      sp.printHTMLfooter();
      return;
    }
    else
      userId = new Integer(value);

    sp.printHTMLheader("RUBiS: View user information");

    try
    {
      initialContext = new InitialContext();
    }
    catch (Exception e)
    {
      sp.printHTML("Cannot get initial context for JNDI: " + e + "<br>");
      sp.printHTMLfooter();
      return;
    }

    // We want to start transactions from client: get UserTransaction
    try
    {
      utx =
        (javax.transaction.UserTransaction) initialContext.lookup(
          Config.UserTransaction);
    }
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup UserTransaction: " + e + "<br>");
      return;
    }

    // Try to find the user corresponding to the userId
    UserHome uHome;
    try
    {
      uHome =
        (UserHome) PortableRemoteObject.narrow(
          initialContext.lookup("UserHome"),
          UserHome.class);
    }
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup Seller: " + e + "<br>");
      sp.printHTMLfooter();
      return;
    }
    try
    {
      User u = uHome.findByPrimaryKey(new UserPK(userId));
      sp.printHTML(u.getHTMLGeneralUserInformation());
    }
    catch (Exception e)
    {
      sp.printHTML("This user does not exist (got exception: " + e + ")<br>");
      sp.printHTMLfooter();
      return;
    }

    // Try to find the comments corresponding for this user
    CommentHome cHome;
    try
    {
      cHome =
        (CommentHome) PortableRemoteObject.narrow(
          initialContext.lookup("CommentHome"),
          CommentHome.class);
    }
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup Comment: " + e + "<br>");
      sp.printHTMLfooter();
      return;
    }
    commentList(cHome, userId, sp, initialContext, utx);
    sp.printHTMLfooter();
  }

}
