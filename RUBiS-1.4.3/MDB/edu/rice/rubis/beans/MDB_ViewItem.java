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
 * This is a stateless session bean used to get the information about an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_ViewItem implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_ViewItem()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      int itemId = request.getInt("itemId");
      int userId = request.getInt("userId");
        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // get the item description
      String html = getItemDescription(itemId, userId);

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
      throw new EJBException("Message traitment failed for MDB_ViewItem: " +e);
    }
  }

  /**
   * Get the full description of an item and the bidding option if userId>0.
   *
   * @param item an <code>Item</code> value
   * @param userId an authenticated user id
   */
  public String getItemDescription(int itemId, int userId) throws RemoteException
  {
    StringBuffer html = new StringBuffer();
    Connection        conn = null;
    PreparedStatement stmt = null;
    ResultSet rs           = null; 

    String itemName=null, endDate=null, startDate=null, description=null, sellerName=null;
    float maxBid=0, initialPrice=0, buyNow=0, reservePrice=0;
    int qty=0, sellerId=-1, nbOfBids=0;
    String  firstBid=null;

    try
    {
      conn = dataSource.getConnection();
      conn.setAutoCommit(false);
      stmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      stmt.setInt(1, itemId);
      rs = stmt.executeQuery();
    }
    catch (SQLException e)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to get the item: " +e);
    }
    try 
    {
      if (!rs.first())
      {
        stmt = conn.prepareStatement("SELECT * FROM old_items WHERE id=?");
        stmt.setInt(1, itemId);
        rs = stmt.executeQuery();
      }
    }
    catch (SQLException e)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to get the item from old items: " +e);
    }
    try
    {
      if (rs.first())
      {
        itemName = rs.getString("name");
        description = rs.getString("description");
        endDate = rs.getString("end_date");
        startDate = rs.getString("start_date");
        initialPrice = rs.getFloat("initial_price");
        reservePrice = rs.getFloat("reserve_price");
        qty = rs.getInt("quantity");
        sellerId = rs.getInt("seller");
        
        maxBid = rs.getFloat("max_bid");
        nbOfBids = rs.getInt("nb_of_bids");
        
        PreparedStatement sellerStmt = null;
        ResultSet sellerResult = null;
        try 
        {
          sellerStmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
          sellerStmt.setInt(1, sellerId);
          sellerResult = sellerStmt.executeQuery();
          // Get the seller's name		 
          if (sellerResult.first()) 
            sellerName = sellerResult.getString("nickname");
          sellerStmt.close();	// close statement
        }
        catch (SQLException e)
        {
          try
          {
            if (sellerStmt != null) sellerStmt.close();	// close statement
            if (conn != null) conn.close();
          }
          catch (Exception ignore)
          {
          }
          throw new RemoteException("Failed to execute Query for seller: " +e);
        }
      }

      if (maxBid == 0)
      {
        firstBid = "none";
        maxBid = initialPrice;
        buyNow = rs.getFloat("buy_now");
      }
      else
      {
        if (qty > 1)
        {
          PreparedStatement bidStmt = null;
          ResultSet bidResult = null;
          try 
          {
            /* Get the qty max first bids and parse bids in this order
               until qty is reached. The bid that reaches qty is the
               current minimum bid. */
            bidStmt = conn.prepareStatement("SELECT bids.id, bids.qty, bids.bid FROM bids WHERE item_id=? ORDER BY bid DESC LIMIT ?");
            bidStmt.setInt(1, itemId);
            bidStmt.setInt(2, qty);
            bidResult = bidStmt.executeQuery();
          }
          catch (SQLException e)
          {
            try
            {
              if (bidStmt != null) bidStmt.close();	// close statement
              if (conn != null) conn.close();
            }
            catch (Exception ignore)
            {
            }
          }

          try
          {
            float bidValue;
            int   numberOfItems = 0;
            while (bidResult.next()) 
            {
              bidValue = bidResult.getFloat("bid");
              numberOfItems += bidResult.getInt("qty");
              if (numberOfItems >= qty)
              {
                maxBid = bidValue;
                break;
              }
            }
            bidStmt.close();	// close statement
          } 
          catch (Exception e)
          {
            try
            {
              if (stmt != null) stmt.close();
              if (conn != null) conn.close();
            }
            catch (Exception ignore)
            {
            }
            throw new RemoteException("Problem while computing current bid: "+e+"<br>");
          }
          Float foo = new Float(maxBid);
          firstBid = foo.toString();
        }
      }
      if (stmt != null) stmt.close();
      if (conn != null) conn.close();
     
      if (userId>0)
      {
        html.append(printHTMLHighlighted("You are ready to bid on: "+itemName));
      }
      else
      {
        html.append(printHTMLHighlighted(itemName));
      }
      html.append("<TABLE>\n"+
                  "<TR><TD>Currently<TD><b><BIG>"+maxBid+"</BIG></b>\n");
      // Check if the reservePrice has been met (if any)
      if (reservePrice > 0)
      { // Has the reserve price been met ?
        if (maxBid >= reservePrice)
          html.append("(The reserve price has been met)\n");
        else
          html.append("(The reserve price has NOT been met)\n");
      }
      html.append("<TR><TD>Quantity<TD><b><BIG>"+qty+"</BIG></b>\n"+
                  "<TR><TD>First bid<TD><b><BIG>"+firstBid+"</BIG></b>\n"+
                  "<TR><TD># of bids<TD><b><BIG>"+nbOfBids+"</BIG></b> (<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewBidHistory?itemId="+itemId+"\">bid history</a>)\n"+
                  "<TR><TD>Seller<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+sellerName+"</a> (<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutCommentAuth?to="+sellerId+"&itemId="+itemId+"\">Leave a comment on this user</a>)\n"+
                  "<TR><TD>Started<TD>"+startDate+"\n"+
                  "<TR><TD>Ends<TD>"+endDate+"\n"+
                  "</TABLE>");
      // Can the user buy this item now ?
      if (buyNow > 0)
        html.append("<p><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.BuyNowAuth?itemId="+itemId+"\">"+
                    "<IMG SRC=\""+BeanConfig.context+"/buy_it_now.jpg\" height=22 width=150></a>"+
                    "  <BIG><b>You can buy this item right now for only $"+buyNow+"</b></BIG><br><p>\n");

      if (userId<=0)
      {
        html.append("<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutBidAuth?itemId="+itemId+"\"><IMG SRC=\""+BeanConfig.context+"/bid_now.jpg\" height=22 width=90> on this item</a>\n");
      }

      html.append(printHTMLHighlighted("Item description"));
      html.append(description);
      html.append("<br><p>\n");

      if (userId>0)
      {
        html.append(printHTMLHighlighted("Bidding"));
        float minBid = maxBid+1;
        html.append("<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreBid\" method=POST>\n"+
                  "<input type=hidden name=minBid value="+minBid+">\n"+
                  "<input type=hidden name=userId value="+userId+">\n"+
                  "<input type=hidden name=itemId value="+itemId+">\n"+
                  "<input type=hidden name=maxQty value="+qty+">\n"+
                  "<center><table>\n"+
                  "<tr><td>Your bid (minimum bid is "+minBid+"):</td>\n"+
                  "<td><input type=text size=10 name=bid></td></tr>\n"+
                  "<tr><td>Your maximum bid:</td>\n"+
                  "<td><input type=text size=10 name=maxBid></td></tr>\n");
        if (qty > 1)
          html.append("<tr><td>Quantity:</td>\n"+
                    "<td><input type=text size=5 name=qty></td></tr>\n");
        else
          html.append("<input type=hidden name=qty value=1>\n");
        html.append("</table><p><input type=submit value=\"Bid now!\"></center><p>\n");
      }
    }
    catch (Exception e)
    {
      throw new RemoteException("Unable to print Item description (exception: "+e+")<br>\n");
    }
    return html.toString();
  }

  /**
   * Construct a html highlighted string.
   * @param msg the message to display
   * @return a string in html format
   * @since 1.1
   */
  public String printHTMLHighlighted(String msg)
  {
    return "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>"+msg+"</B></FONT></TD></TR>\n</TABLE><p>\n";
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
