package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.EJBException;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.Serializable;
import javax.transaction.UserTransaction;
import java.net.URLEncoder;

/**
 * This is a stateless session bean used to give to a user the information about himself.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_AboutMe implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_AboutMe()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      String username = request.getString("username");
      String password = request.getString("password");
        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);
      // get the post comment form
      String html = getAboutMe(username, password);
      // send the reply
      TemporaryTopic temporaryTopic = (TemporaryTopic) request.getJMSReplyTo();
      if (temporaryTopic != null)
      {
        // create a connection
        connection = connectionFactory.createTopicConnection();
        // create a session: no transaction, auto ack
        session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TextMessage reply = session.createTextMessage();
        reply.setJMSCorrelationID(correlationID);
        reply.setText(html);

        replier = session.createPublisher(null); // unidentified publisher
        connection.start();
        replier.publish(temporaryTopic, reply);
        replier.close();
        session.close();
        connection.stop();
        connection.close();
      }
    }
    catch (Exception e)
    {
      throw new EJBException("Message traitment failed for MDB_AboutMe: " +e);
    }
  }

  /**
   * Authenticate the user and get the information about the user.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getAboutMe(String username, String password) throws RemoteException
  {
       //   UserTransaction utx    = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;
    int          userId       = -1;
    StringBuffer html      = new StringBuffer();

    // Authenticate the user
      if ((username != null && !username.equals("")) || (password != null && !password.equals("")))
      {
        TopicConnection authConnection;
        TopicSession authSession;
        Topic authTopic;
        try 
        {
          // create a connection
          authConnection = connectionFactory.createTopicConnection();
          // lookup the destination
          authTopic = (Topic) initialContext.lookup(BeanConfig.PrefixTopicName+"topicAuth");
          // create a session
          authSession  = authConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE); // no transaction and auto ack
        } 
        catch (Exception e)
        {
          throw new EJBException("Cannot connect to message bean MDB_Auth : " +e+"<br>");
        }
        try 
        {
          // create a requestor to receive the reply
          TopicRequestor requestor = new TopicRequestor(authSession, authTopic);
          // create a message
          MapMessage m = authSession.createMapMessage();
          // set parameters
          m.setStringProperty("nickname", username);
          m.setStringProperty("password", password);
          m.setJMSCorrelationID("auth");
          // send the message and receive the reply
          authConnection.start(); // allows message to be delivered (default is connection stopped)
          MapMessage authReply = (MapMessage)requestor.request(m);
          authConnection.stop();
          // read the reply
          userId = authReply.getInt("userId");
          // close connection and session
          requestor.close(); // also close the session
          authConnection.close();
        } 
        catch (Exception e)
        {
          throw new EJBException("user authentication failed: " +e+"<br>");
        }
        if (userId == -1)
        {
           html.append("You don't have an account on RUBiS!<br>You have to register first.<br>");
           return html.toString();
        }
      }
    // Try to find the user corresponding to the userId
    String firstname = null, lastname = null, nickname = null, email = null, date = null;
    int rating = 0;
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT * FROM users WHERE id=?");
      stmt.setInt(1, userId);
      rs = stmt.executeQuery();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Failed to execute Query for user: " +e);
    }
    try 
    {
      if (rs.first())
      {
        firstname = rs.getString("firstname");
        lastname = rs.getString("lastname");
        nickname = rs.getString("nickname");
        email = rs.getString("email");
        date = rs.getString("creation_date");
        rating = rs.getInt("rating");
      }
      stmt.close();
      conn.close();
      html.append("<h2>Information about "+nickname+"<br></h2>");
      html.append("Real life name : "+firstname+" "+lastname+"<br>");
      html.append("Email address  : "+email+"<br>");
      html.append("User since     : "+date+"<br>");
      html.append("Current rating : <b>"+rating+"</b><br>");
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("This user does not exist (got exception: " +e+")<br>");
    }

    try 
    {
       //     utx = messageDrivenContext.getUserTransaction();
       //     utx.begin();
      conn = dataSource.getConnection();
      html.append(listItem(userId, conn));
      html.append(listBoughtItems(userId, conn));
      html.append(listWonItems(userId, conn));
      html.append(listBids(userId, username, password, conn));
      html.append(listComments(userId, conn));
      conn.close();
      //      utx.commit();
    } 
    catch (Exception e)
    {
      try { conn.close(); } catch (Exception ignore) {}
       //     try { utx.rollback(); } catch (Exception ignore) {}
      throw new RemoteException("Cannot get information about items and bids: " +e+"<br>");
    }
    return html.toString();
  }
                   
  /** List items the user is currently selling and sold in the past 30 days */
  public String listItem(int userId, Connection conn) throws RemoteException
  {
    StringBuffer sell = new StringBuffer();
    ResultSet currentSellings = null;
    ResultSet pastSellings = null;
    PreparedStatement pastStmt = null;
    PreparedStatement currentStmt = null;

    String itemName, endDate, startDate;
    float currentPrice=0, initialPrice=0, buyNow=0, reservePrice=0;
    int quantity=0, itemId=-1;

    // Retrieve ItemHome to get the names of the items the user sold
    try 
    {
      currentStmt = conn.prepareStatement("SELECT * FROM items WHERE items.seller=? AND items.end_date>=NOW()");
      currentStmt.setInt(1, userId);
      currentSellings = currentStmt.executeQuery();
    }
    catch (Exception e)
    {
      try { currentStmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting current sellings list: " +e+"<br>");
    }
    try 
    {
      pastStmt = conn.prepareStatement("SELECT * FROM old_items WHERE old_items.seller=? AND TO_DAYS(NOW()) - TO_DAYS(old_items.end_date) < 30");
      pastStmt.setInt(1, userId);
      pastSellings = pastStmt.executeQuery();
    }
    catch (Exception e)
    {
      try { pastStmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting past sellings list: " +e+"<br>");
    }
    try
    {
      if (currentSellings.first())
      {
        // display current sellings
        sell.append(printSellHeader("Items you are currently selling."));
        do
        {
          // Get the name of the items
          try
          {
            itemId = currentSellings.getInt("id");
            itemName = currentSellings.getString("name");
            endDate = currentSellings.getString("end_date");
            startDate = currentSellings.getString("start_date");
            initialPrice = currentSellings.getFloat("initial_price");
            reservePrice = currentSellings.getFloat("reserve_price");
            buyNow = currentSellings.getFloat("buy_now");
            quantity = currentSellings.getInt("quantity");

            currentPrice = currentSellings.getFloat("max_bid");
            if (currentPrice <initialPrice)
              currentPrice = initialPrice;

          }
          catch (Exception e) 
          {
            try { currentStmt.close(); } catch (Exception ignore) {}
            try { conn.close(); } catch (Exception ignore) {}
            throw new RemoteException("Exception getting item: " + e +"<br>");
          }
          // display information about the item
          sell.append(printSell(itemId, itemName,  initialPrice, currentPrice,  quantity, reservePrice, buyNow, startDate, endDate));
        }
        while (currentSellings.next());
        currentStmt.close();
        sell.append(printItemFooter());
      }
      else
      {
        currentStmt.close();
        sell.append("<br>");
        sell.append(printHTMLHighlighted("<h3>You are currently selling no item.</h3>"));
      }
    }
    catch (Exception e) 
    {
      try { currentStmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting current items in sell: " + e +"<br>");
    }
    try
    {
      if (pastSellings.first())
      {
        // display past sellings
        sell.append("<br>");
        sell.append(printSellHeader("Items you sold in the last 30 days."));
        do 
        {
          // Get the name of the items
          try
          {
            itemId = pastSellings.getInt("id");
            itemName = pastSellings.getString("name");
            endDate = pastSellings.getString("end_date");
            startDate = pastSellings.getString("start_date");
            initialPrice = pastSellings.getFloat("initial_price");
            reservePrice = pastSellings.getFloat("reserve_price");
            buyNow = pastSellings.getFloat("buy_now");
            quantity = pastSellings.getInt("quantity");

            currentPrice = pastSellings.getFloat("max_bid");
            if (currentPrice <initialPrice)
              currentPrice = initialPrice;
          }
          catch (Exception e) 
          {
            try { pastStmt.close(); } catch (Exception ignore) {}
            try { conn.close(); } catch (Exception ignore) {}
            throw new RemoteException("Exception getting sold item: " + e +"<br>");
          }
          // display information about the item
          sell.append(printSell(itemId, itemName,  initialPrice, currentPrice,  quantity, reservePrice, buyNow, startDate, endDate));
        }
        while (pastSellings.next());
        pastStmt.close();
        sell.append(printItemFooter());
      }
      else
      {
        sell.append(printHTMLHighlighted("<br><h3>You didn't sell any item in the past 30 days.</h3>"));
        pastStmt.close();
        return sell.toString();
      }
    }
    catch (Exception e) 
    {
      try { pastStmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting sold items: " + e +"<br>");
    }
    return sell.toString();
  }

  /** List items the user bought in the last 30 days*/
  public String listBoughtItems(int userId, Connection conn) throws RemoteException 
  {
    ResultSet buy = null;
    PreparedStatement stmt = null;
    String itemName = null, sellerName = null;
    int quantity=0, sellerId=-1, itemId=-1;
    float buyNow=0;
    StringBuffer html = new StringBuffer();

    // Get the list of items the user bought
    try 
    {
      stmt = conn.prepareStatement("SELECT * FROM buy_now WHERE buy_now.buyer_id=? AND TO_DAYS(NOW()) - TO_DAYS(buy_now.date)<=30");
      stmt.setInt(1, userId);
      buy = stmt.executeQuery();
      if (!buy.first())
      {
        stmt.close();
        return printHTMLHighlighted("<br><h3>You didn't buy any item in the last 30 days.</h3><br>");
      }
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting bought items list: " +e+"<br>");
    }
    html.append(printUserBoughtItemHeader());
    PreparedStatement itemStmt = null;
    PreparedStatement sellerStmt = null;
    try
    {
      itemStmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      sellerStmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      ResultSet itemRS = null;
      ResultSet sellerResult = null;
      do
      {
	itemId = buy.getInt("item_id");
	quantity = buy.getInt("qty");
        // Get the name of the items
        
        itemStmt.setInt(1, itemId);
        itemRS = itemStmt.executeQuery();
        if (itemRS.first())
        {
          itemName = itemRS.getString("name");
          sellerId = itemRS.getInt("seller");
          buyNow = itemRS.getFloat("buy_now");
        }

        sellerStmt.setInt(1, sellerId);
        sellerResult = sellerStmt.executeQuery();
        // Get the seller's name		 
        if (sellerResult.first()) 
          sellerName = sellerResult.getString("nickname");

        // display information about the item
        html.append(printUserBoughtItem(itemId, itemName, quantity, buyNow, sellerId, sellerName));
      }
      while (buy.next());
      stmt.close();
      itemStmt.close();
      sellerStmt.close();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { itemStmt.close(); } catch (Exception ignore) {}
      try { sellerStmt.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting bought items: " +e+"<br>");
    }
    html.append(printItemFooter());
    return html.toString();

  }

  /** List items the user won in the last 30 days*/
  public String listWonItems(int userId, Connection conn) throws RemoteException 
  {
    int sellerId=-1, itemId=-1;  
    float currentPrice=0, initialPrice=0;
    String itemName = null, sellerName = null;
    ResultSet won = null;
    PreparedStatement stmt = null;
    StringBuffer html;

    // Get the list of the user's won items
    try 
    {
      stmt = conn.prepareStatement("SELECT item_id FROM bids, old_items WHERE bids.user_id=? AND bids.item_id=old_items.id AND TO_DAYS(NOW()) - TO_DAYS(old_items.end_date) < 30 GROUP BY item_id");
      stmt.setInt(1, userId);
      won = stmt.executeQuery();
      if (!won.first())
      {
        stmt.close();
        return printHTMLHighlighted("<br><h3>You didn't win any item in the last 30 days.</h3><br>");
      }
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting won items list: " +e+"<br>");
    }
    html = new StringBuffer(printUserWonItemHeader());

    PreparedStatement itemStmt = null;
    PreparedStatement sellerStmt = null;
    try
    {
      itemStmt = conn.prepareStatement("SELECT * FROM old_items WHERE id=?");
      sellerStmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      ResultSet itemRS = null;
      ResultSet sellerResult = null;
      do 
      {
	itemId = won.getInt("item_id");
        // Get the name of the items
        itemStmt.setInt(1, itemId);
        itemRS = itemStmt.executeQuery();
        if (itemRS.first())
        {
          itemName = itemRS.getString("name");
          sellerId = itemRS.getInt("seller");
          initialPrice = itemRS.getFloat("initial_price");

          currentPrice = itemRS.getFloat("max_bid");
          if (currentPrice <initialPrice)
            currentPrice = initialPrice;
        }

        sellerStmt.setInt(1, sellerId);
        sellerResult = sellerStmt.executeQuery();
        // Get the seller's name		 
        if (sellerResult.first()) 
          sellerName = sellerResult.getString("nickname"); 

        // display information about the item
        html.append(printUserWonItem(itemId, itemName, currentPrice, sellerId, sellerName));
      }
      while (won.next());
      stmt.close();
      itemStmt.close();
      sellerStmt.close();
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { itemStmt.close(); } catch (Exception ignore) {}
      try { sellerStmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting won items: " +e+"<br>");
    }
    html.append(printItemFooter());
    return html.toString();
  }


  /** List comments about the user */
  public String listComments(int userId, Connection conn) throws RemoteException
  {
    ResultSet rs = null;
    PreparedStatement stmt = null;
    String date = null, comment = null;
    int authorId = -1;
    StringBuffer html = null;

    // Try to find the comment corresponding to the user
    try
    {
      stmt = conn.prepareStatement("SELECT * FROM comments WHERE to_user_id=?");
      stmt.setInt(1, userId);
      rs = stmt.executeQuery();
      if (!rs.first()) 
      {
        stmt.close();
        return printHTMLHighlighted(("<br><h3>There is no comment yet for this user.</h3><br>"));
      }
      else
        html = new StringBuffer(printHTMLHighlighted("<h3>Comments for this user</h3><br>"));
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Failed to execute Query for list of comments: " +e);
    }
    html.append(printCommentHeader());
    PreparedStatement authorStmt = null;
    try
    {
    // Display each comment and the name of its author
      authorStmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      ResultSet authorRS = null;
      do 
      {
        comment = rs.getString("comment");
        date = rs.getString("date");
        authorId = rs.getInt("from_user_id");

        String authorName = "none";
        try
        {
          authorStmt.setInt(1, authorId);
          authorRS = authorStmt.executeQuery();
          if (authorRS.first())
            authorName = authorRS.getString("nickname");
        }
        catch (Exception e)
        {
          throw new RemoteException("Failed to execute Query for the comment author: " +e);
        }
        html.append(printComment(authorName, date, comment, authorId));
      }
      while (rs.next());
      authorStmt.close();
      stmt.close();
    }
    catch (Exception e)
    {
      try { authorStmt.close(); } catch (Exception ignore) {}
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Failed to get comments list: " +e);
    }
    html.append(printCommentFooter());
    return html.toString();
  }

  /** List items the user put a bid on in the last 30 days*/
  public String listBids(int userId, String username, String password, Connection conn) throws RemoteException 
  {
    float currentPrice = 0, initialPrice = 0, maxBid = 0;
    String itemName = null, sellerName = null, startDate = null, endDate = null;
    int sellerId = -1, quantity = 0, itemId = -1;
    ResultSet bid = null;
    PreparedStatement stmt = null;
    StringBuffer html = null;

    // Get the list of the user's last bids
    try 
    {
      stmt = conn.prepareStatement("SELECT item_id, bids.max_bid FROM bids, items WHERE bids.user_id=? AND bids.item_id=items.id AND items.end_date>=NOW() GROUP BY item_id");
      stmt.setInt(1, userId);
      bid = stmt.executeQuery();
      if (!bid.first())
      {
        stmt.close();
        return printHTMLHighlighted("<h3>You didn't put any bid.</h3>");
      }
    }
    catch (Exception e)
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting bids list: " +e+"<br>");
    }
    html = new StringBuffer(printUserBidsHeader());

    PreparedStatement itemStmt = null;
    PreparedStatement sellerStmt = null;
    try
    {
      itemStmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      sellerStmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      ResultSet rs = null;
      ResultSet sellerResult = null;
      do 
      {
	itemId = bid.getInt("item_id");
	maxBid = bid.getFloat("max_bid"); // maximum bid

        itemStmt.setInt(1, itemId);
        rs = itemStmt.executeQuery();

        // Get the name of the items
        if (rs.first()) 
        {
          itemName = rs.getString("name");
          initialPrice = rs.getFloat("initial_price");
          quantity = rs.getInt("quantity");
          startDate = rs.getString("start_date");
          endDate = rs.getString("end_date");  
          sellerId = rs.getInt("seller");

          currentPrice = rs.getFloat("max_bid"); // currentPrice
          if (currentPrice <initialPrice)
            currentPrice = initialPrice;
        }

        sellerStmt.setInt(1, sellerId);
        sellerResult = sellerStmt.executeQuery();
        // Get the seller's name		 
        if (sellerResult.first()) 
          sellerName = sellerResult.getString("nickname");

        //  display information about user's bids
        html.append(printItemUserHasBidOn(itemId, itemName, initialPrice, maxBid, currentPrice, quantity, startDate, endDate, sellerId, sellerName, username, password));
      }
      while(bid.next());
      stmt.close();
      itemStmt.close();
      sellerStmt.close();
    }
    catch (Exception e) 
    {
      try { stmt.close(); } catch (Exception ignore) {}
      try { itemStmt.close(); } catch (Exception ignore) {}
      try { sellerStmt.close(); } catch (Exception ignore) {}
      try { conn.close(); } catch (Exception ignore) {}
      throw new RemoteException("Exception getting items the user has bid on: " + e +"<br>");
    }
    html.append(printItemFooter());
    return html.toString();
  }

  /** 
   * user's bought items list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printUserBoughtItemHeader()
  {
    return "<br>"+
      printHTMLHighlighted("<p><h3>Items you bouhgt in the past 30 days.</h3>\n")+
      "<TABLE border=\"1\" summary=\"List of items\">\n"+
      "<THEAD>\n"+
      "<TR><TH>Designation<TH>Quantity<TH>Price you bought it<TH>Seller"+
      "<TBODY>\n";
  }

  /** 
   * user's won items list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printUserWonItemHeader()
  {
    return "<br>"+
      printHTMLHighlighted("<p><h3>Items you won in the past 30 days.</h3>\n")+
      "<TABLE border=\"1\" summary=\"List of items\">\n"+
      "<THEAD>\n"+
      "<TR><TH>Designation<TH>Price you bought it<TH>Seller"+
      "<TBODY>\n";
  }

  /** 
   * user's bids list header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printUserBidsHeader()
  {
    return "<br>"+
      printHTMLHighlighted("<p><h3>Items you have bid on.</h3>\n")+
      "<TABLE border=\"1\" summary=\"Items You've bid on\">\n"+
      "<THEAD>\n"+
      "<TR><TH>Designation<TH>Initial Price<TH>Current price<TH>Your max bid<TH>Quantity"+
      "<TH>Start Date<TH>End Date<TH>Seller<TH>Put a new bid\n"+
      "<TBODY>\n";
  }


  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printUserBoughtItem(int id, String name, int qty, float buyNow, int sellerId, String sellerName) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+"</a>\n"+
      "<TD>"+qty+"\n"+"<TD>"+buyNow+"\n"+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+sellerName+"</a>\n";
  }

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code (Warning last link must be completed by servlet)
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemUserHasBidOn(int id, String name, float initialPrice, float maxBid, float currentPrice, int quantity, String startDate, String endDate, int sellerId, String sellerName, String username, String password) throws RemoteException
  {
    StringBuffer html = new StringBuffer("<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+
      "<TD>"+initialPrice+"<TD>"+currentPrice+"<TD>"+maxBid+"<TD>"+quantity+"<TD>"+startDate+"<TD>"+endDate+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+sellerName+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutBid?itemId="+id);
    html.append("&nickname="+URLEncoder.encode(username)+"&password="+URLEncoder.encode(password)+"\"><IMG SRC=\""+BeanConfig.context+"/bid_now.jpg\" height=22 width=90></a>\n");
    return html.toString();
  }


  /**
   * Display item information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printSell(int id, String name, float initialPrice, float maxBid, int quantity, float reservePrice, float buyNow, String startDate, String endDate) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+
      "<TD>"+initialPrice+"<TD>"+maxBid+"<TD>"+quantity+"<TD>"+reservePrice+"<TD>"+buyNow+"<TD>"+startDate+"<TD>"+endDate+"\n";
  }
 
  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printUserWonItem(int id, String name, float maxBid, int sellerId, String sellerName) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+"</a>\n"+
      "<TD>"+maxBid+"\n"+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+sellerName+"</a>\n";
  }

  /** 
   * user's sellings header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printSellHeader(String title)
  {
    return printHTMLHighlighted("<p><h3>"+title+"</h3>\n")+
      "<TABLE border=\"1\" summary=\"List of items\">\n"+
      "<THEAD>\n"+
      "<TR><TH>Designation<TH>Initial Price<TH>Current price<TH>Quantity<TH>ReservePrice<TH>Buy Now"+
      "<TH>Start Date<TH>End Date\n"+
      "<TBODY>\n";
  }


  /** 
   * Item footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printItemFooter()
  {
    return "</TABLE>\n";
  }

  /** 
   * Comment header printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentHeader()
  {
    return "<DL>\n";
  }

  /**
   * Display comment information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printComment(String userName, String date, String comment, int fromUserId) throws RemoteException
  {
    return "<DT><b><BIG><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+fromUserId+"\">"+userName+"</a></BIG></b>"+
      " wrote the "+date+"<DD><i>"+comment+"</i><p>\n";
  }

  /** 
   * Comment footer printed function
   *
   * @return a string in html format
   * @since 1.1
   */
  public String printCommentFooter()
  {
    return "</DL>\n";
  }

  /**
   * Construct a html highlighted string.
   * @param msg the message to display
   * @return a string in html format
   * @since 1.1
   */
  public String printHTMLHighlighted(String msg)
  {
    return "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>" + msg + "</B></FONT></TD></TR>\n</TABLE><p>\n";
}

  // ======================== EJB related methods ============================

  /** 
   * Set the associated context. The container call this method
   * after the instance creation. 
   * The enterprise Bean instance should store the reference to the context
   * object in an instance variable. 
   * This method is called with no transaction context.
   *
   * @param MessageDrivenContext A MessageDrivenContext interface for the instance.
   * @throws EJBException Thrown by the method to indicate a failure caused by
   * a system-level error.
   */
  public void setMessageDrivenContext(MessageDrivenContext ctx)
  {
    messageDrivenContext = ctx;
    if (dataSource == null)
    {
      // Finds DataSource from JNDI
      try
      {
        initialContext = new InitialContext(); 
        dataSource = (DataSource)initialContext.lookup("java:comp/env/jdbc/rubis");
      }
      catch (Exception e) 
      {
        throw new EJBException("Cannot get JNDI InitialContext");
      }
    }
  }

  /**
   * The Message driven  bean must define an ejbCreate methods with no args.
   *
   */
  public void ejbCreate() 
  {

  }
 
  /**
   * A container invokes this method before it ends the life of the message-driven object. 
   * This happens when a container decides to terminate the message-driven object. 
   *
   * This method is called with no transaction context. 
   *
   * @throws EJBException Thrown by the method to indicate a failure caused by
   * a system-level error.
   */
  public void ejbRemove() {}


}
