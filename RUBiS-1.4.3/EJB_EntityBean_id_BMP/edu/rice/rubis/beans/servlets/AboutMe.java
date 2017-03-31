package edu.rice.rubis.beans.servlets;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import edu.rice.rubis.beans.Bid;
import edu.rice.rubis.beans.BidHome;
import edu.rice.rubis.beans.BidPK;
import edu.rice.rubis.beans.BuyNowHome;
import edu.rice.rubis.beans.Comment;
import edu.rice.rubis.beans.CommentHome;
import edu.rice.rubis.beans.Item;
import edu.rice.rubis.beans.ItemHome;
import edu.rice.rubis.beans.ItemPK;
import edu.rice.rubis.beans.Query;
import edu.rice.rubis.beans.QueryHome;
import edu.rice.rubis.beans.User;
import edu.rice.rubis.beans.UserHome;
import edu.rice.rubis.beans.UserPK;

/**
 * This servlets displays general information about the user loged in
 * and about his current bids or items to sell.
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class AboutMe extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    //sp.printHTMLheader("RUBiS ERROR: About me");
    sp.printHTML(
      "<h3>Your request has not been processed due to the following error :</h3><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /** List items the user is currently selling and sold in the past 30 days */
  private void listItem(Integer userId, ItemHome iHome, ServletPrinter sp)
  {
    Item item;
    Collection currentItemList, pastItemList;

    try
    {
      currentItemList = iHome.findUserCurrentSellings(userId);
      pastItemList = iHome.findUserPastSellings(userId);
    }
    catch (Exception e)
    {
      printError("Exception getting item list: " + e + "<br>", sp);
      return;
    }

    if ((currentItemList == null) || (currentItemList.isEmpty()))
    {
      sp.printHTML("<br>");
      sp.printHTMLHighlighted("<h3>You are currently selling no item.</h3>");
    }
    else
    {
      // display current sellings
      sp.printSellHeader("Items you are currently selling.");

      Iterator it = currentItemList.iterator();
      while (it.hasNext())
      {
        // Get the name of the items
        try
        {
          item = (Item) it.next();
        }
        catch (Exception e)
        {
          printError("Exception getting item: " + e + "<br>", sp);
          return;
        }
        // display information about the item
        sp.printSell(item);
      }
      sp.printItemFooter();
    }

    if ((pastItemList == null) || (pastItemList.isEmpty()))
    {
      sp.printHTML("<br>");
      sp.printHTMLHighlighted("<h3>You didn't sell any item.</h3>");
      return;
    }
    // display past sellings
    sp.printHTML("<br>");
    sp.printSellHeader("Items you sold in the last 30 days.");

    Iterator it = pastItemList.iterator();
    while (it.hasNext())
    {
      // Get the name of the items
      try
      {
        item = (Item) it.next();
      }
      catch (Exception e)
      {
        printError("Exception getting item: " + e + "<br>", sp);
        return;
      }
      // display information about the item
      sp.printSell(item);
    }
    sp.printItemFooter();
  }

  /** List items the user bought in the last 30 days*/
  private void listBoughtItems(Integer userId, ItemHome iHome, ServletPrinter sp, Context initialContext)
  {
    BuyNowHome buyHome;
    edu.rice.rubis.beans.BuyNow buy;
    Item item;
    Collection buyList = null;
    int quantity;

    // Get the list of items the user bought
    try
    {
      buyHome =
        (BuyNowHome) PortableRemoteObject.narrow(
          initialContext.lookup("BuyNowHome"),
          BuyNowHome.class);
    }
    catch (Exception e)
    {
      printError("Cannot lookup BuyNow: " + e + "<br>", sp);
      return;
    }
    try
    {
      buyList = buyHome.findUserBuyNow(userId);
    }
    catch (Exception e)
    {
      printError("Exception getting item list (buy now): " + e + "<br>", sp);
      return;
    }

    if ((buyList == null) || (buyList.isEmpty()))
    {
      sp.printHTML("<br>");
      sp.printHTMLHighlighted(
        "<h3>You didn't buy any item in the last 30 days.</h3>");
      sp.printHTML("<br>");
      return;
    }
    sp.printUserBoughtItemHeader();

    Iterator it = buyList.iterator();
    while (it.hasNext())
    {
      // Get the name of the items
      try
      {
        buy = (edu.rice.rubis.beans.BuyNow) it.next();
        //buy = buyHome.findByPrimaryKey((BuyNowPK)it.next());
        quantity = buy.getQuantity();
      }
      catch (Exception e)
      {
        printError("Exception getting buyNow: " + e + "<br>", sp);
        return;
      }
      try
      {
        item = iHome.findByPrimaryKey(new ItemPK(buy.getItemId()));
      }
      catch (Exception e)
      {
        printError("Exception getting item: " + e + "<br>", sp);
        return;
      }
      // display information about the item
      sp.printUserBoughtItem(item, quantity);
    }
    sp.printItemFooter();

  }

  /** List items the user won in the last 30 days*/
  private void listWonItems(Integer userId, ItemHome iHome, QueryHome qHome, ServletPrinter sp)
  {
    Enumeration wonList = null;
    Query q;
    Item item;
    float price;
    String name;

    // Get the list of the user's won items
    try
    {
      q = qHome.create();
      wonList = q.getUserWonItems(userId).elements();
    }
    catch (Exception e)
    {
      sp.printHTML("Exception getting won items list: " + e + "<br>");
      return;
    }
    if ((wonList == null) || (!wonList.hasMoreElements()))
    {
      sp.printHTML("<br>");
      sp.printHTMLHighlighted(
        "<h3>You didn't win any item in the last 30 days.</h3>");
      sp.printHTML("<br>");
      return;
    }
    sp.printUserWonItemHeader();

    while (wonList.hasMoreElements())
    {
      // Get the name of the items
      try
      {
        item = iHome.findByPrimaryKey((ItemPK) wonList.nextElement());
      }
      catch (Exception e)
      {
        printError("Exception getting item: " + e + "<br>", sp);
        return;
      }
      // display information about the item
      sp.printUserWonItem(item);
    }
    sp.printItemFooter();

  }

  /** List comments about the user */
  private void listComment(CommentHome home, Integer userId, UserHome uHome, ServletPrinter sp, UserTransaction utx)
  {
    Collection list;
    Comment comment;

    try
    {
      utx.begin(); // faster if made inside a Tx
      list = home.findByToUser(userId);
      sp.printHTML("<br>");
      if (list.isEmpty())
        sp.printHTMLHighlighted(
          "<h3>There is no comment yet for this user.</h3>");
      else
        sp.printHTMLHighlighted("<h3>Comments for this user</h3>");
      sp.printHTML("<br>");
      sp.printCommentHeader();
      // Display each comment and the name of its author
      Iterator it = list.iterator();
      while (it.hasNext())
      {
        comment = (Comment) it.next();
        String userName;
        try
        {
          User u = uHome.findByPrimaryKey(new UserPK(comment.getFromUserId()));
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

  /** List items the user put a bid on in the last 30 days*/
  private void listBids(
    Integer userId,
    String username,
    String password,
    ItemHome iHome,
    QueryHome qHome,
    ServletPrinter sp,
    Context initialContext)
  {
    Enumeration bidList = null;
    Query q;
    BidHome bidHome;
    Bid bid;
    Item item;
    float price;
    String name;

    // Get the list of the user's last bids
    try
    {
      q = qHome.create();
      bidList = q.getUserBids(userId).elements();
    }
    catch (Exception e)
    {
      sp.printHTML("Exception getting bids list: " + e + "<br>");
      return;
    }
    if ((bidList == null) || (!bidList.hasMoreElements()))
    {
      sp.printHTMLHighlighted("<h3>You didn't put any bid.</h3>");
      return;
    }

    // Lookup bid home interface
    try
    {
      bidHome =
        (BidHome) PortableRemoteObject.narrow(
          initialContext.lookup("BidHome"),
          BidHome.class);
    }
    catch (Exception e)
    {
      printError("Cannot lookup Bid: " + e + "<br>", sp);
      return;
    }

    sp.printUserBidsHeader();

    while (bidList.hasMoreElements())
    {
      // Get the amount of the last bids
      try
      {
        bid = bidHome.findByPrimaryKey((BidPK) bidList.nextElement());
      }
      catch (Exception e)
      {
        printError("Exception getting bid: " + e + "<br>", sp);
        return;
      }

      // Get the name of the items
      try
      {
        item = iHome.findByPrimaryKey(new ItemPK(bid.getItemId()));
      }
      catch (Exception e)
      {
        printError("Exception getting item: " + e + "<br>", sp);
        return;
      }
      //  display information about user's bids

      sp.printItemUserHasBidOn(bid, item, username, password);
    }
    sp.printItemFooter();
  }

  /**
   * Call <code>doPost</code> method.
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
   * Check username and password and build the web page that display the information about
   * the loged in user.
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
    String password = null, username = null;
    Integer userId = null;

    sp = new ServletPrinter(response, "About me");

    username = request.getParameter("nickname");
    password = request.getParameter("password");
    // Authenticate the user
    if ((username != null && username != "")
      || (password != null && password != ""))
    {
      try
      {
        initialContext = new InitialContext();
      }
      catch (Exception e)
      {
        printError("Cannot get initial context for JNDI: " + e + "<br>", sp);
        return;
      }
      Auth auth = new Auth(initialContext, sp);
      int id = auth.authenticate(username, password);
      if (id == -1)
      {
        printError("You don't have an account on RUBiS!<br>You have to register first.<br>", sp);
        return;
      }
      userId = new Integer(id);
    }
    else
    {
      printError(" You must provide valid username and password.", sp);
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
      printError("Cannot lookup UserTransaction: " + e + "<br>", sp);
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
      printError("Cannot lookup User: " + e + "<br>", sp);
      return;
    }
    try
    {
      User u = uHome.findByPrimaryKey(new UserPK(userId));
      sp.printHTMLheader("RUBiS: About " + u.getNickName());
      sp.printHTML(u.getHTMLGeneralUserInformation());

    }
    catch (Exception e)
    {
      printError("This user does not exist (got exception: " + e + ")<br>", sp);
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

    // Retrieve ItemHome
    ItemHome iHome;
    try
    {
      iHome =
        (ItemHome) PortableRemoteObject.narrow(
          initialContext.lookup("ItemHome"),
          ItemHome.class);
    }
    catch (Exception e)
    {
      printError("Cannot lookup item: " + e + "<br>", sp);
      return;
    }

    // Connecting to Query Home thru JNDI
    QueryHome qHome;
    try
    {
      qHome =
        (QueryHome) PortableRemoteObject.narrow(
          initialContext.lookup("QueryHome"),
          QueryHome.class);
    }
    catch (Exception e)
    {
      printError("Cannot lookup Query: " + e + "<br>", sp);
      return;
    }

    listBids(userId, username, password, iHome, qHome, sp, initialContext);
    listItem(userId, iHome, sp);
    listWonItems(userId, iHome, qHome, sp);
    listBoughtItems(userId, iHome, sp, initialContext);
    listComment(cHome, userId, uHome, sp, utx);

    sp.printHTMLfooter();
  }

}
