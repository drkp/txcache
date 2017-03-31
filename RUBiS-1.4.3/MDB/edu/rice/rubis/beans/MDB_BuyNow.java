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

/**
 * This is a stateless session bean used to build the html form to buy an item.
 *  
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class MDB_BuyNow implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_BuyNow()
  {

  }

  public void onMessage(Message message)
  {
    try
    {
      MapMessage request = (MapMessage)message;
      String correlationID = request.getJMSCorrelationID();
      int itemId = request.getInt("itemId");
      String username = request.getString("username");
      String password = request.getString("password");  

        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);

      // get the post comment form
      String html = getBuyNowForm(itemId, username, password);

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
      throw new EJBException("Message traitment failed for MDB_BuyNow: " +e);
    }
  }

  /**
   * Authenticate the user and get the information to build the html form.
   *
   * @return a string in html format
   * @since 1.1
   */
  public String getBuyNowForm(int itemId, String username, String password) throws RemoteException
  {
    int userId = -1;
    StringBuffer html = new StringBuffer();
    PreparedStatement stmt = null;
    ResultSet rs           = null;
    Connection conn        = null;

    // Authenticate the user who want to buy
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
    // Try to find the Item corresponding to the Item ID
    String itemName = null, description = null;
    String startDate = null, endDate = null, sellerName = null;
    int quantity = 0, sellerId = -1;
    float buyNow = 0;
    try
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT * FROM items WHERE id=?");
      stmt.setInt(1, itemId);
      rs = stmt.executeQuery();
      if (rs.first())
      {
        itemName = rs.getString("name");
        description = rs.getString("description");
        startDate = rs.getString("start_date");
        endDate = rs.getString("end_date");
        buyNow = rs.getFloat("buy_now");
        quantity = rs.getInt("quantity");
        sellerId = rs.getInt("seller");
      }
      stmt.close();
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
      throw new RemoteException("Failed to execute Query for item: " +e);
    }

    try
    {
      stmt = conn.prepareStatement("SELECT nickname FROM users WHERE id=?");
      stmt.setInt(1, sellerId);
      ResultSet srs = stmt.executeQuery();
      if (srs.first())
      {
        sellerName = srs.getString("nickname");
      }
      stmt.close();
      conn.close();
    }
    catch (SQLException s)
    {
      try
      {
        if (stmt != null) stmt.close();
        if (conn != null) conn.close();
      }
      catch (Exception ignore)
      {
      }
      throw new RemoteException("Failed to execute Query for seller: " +s);
    }

    // Display the form for buying the item
    html.append("<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>You are ready to buy this item: "+itemName+"</B></FONT></TD></TR>\n</TABLE><p>\n");
    try
    {
      html.append(printItemDescriptionToBuyNow(itemId, itemName, description, buyNow, quantity, sellerId, sellerName, startDate, endDate, userId));
    }
    catch (Exception e)
    {
      throw new RemoteException("Unable to print Item description: " +e);
    }

    return html.toString();
  }
 
  /**
   * Display item information for the Buy Now servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemDescriptionToBuyNow(int itemId, String itemName, String description, float buyNow, int quantity, int sellerId, String sellerName, String startDate, String endDate, int userId) throws RemoteException
  {
    StringBuffer result = new StringBuffer("<TABLE>\n"+"<TR><TD>Quantity<TD><b><BIG>"+quantity+"</BIG></b>\n");
    result.append("<TR><TD>Seller<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+
                  sellerName+"</a> (<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutCommentAuth?to="+sellerId+"&itemId="+itemId+"\">Leave a comment on this user</a>)\n"+
                  "<TR><TD>Started<TD>"+startDate+"\n"+"<TR><TD>Ends<TD>"+endDate+"\n"+
                  "</TABLE>"+
                  "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n"+
                  "<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>Item description</B></FONT></TD></TR>\n"+
                  "</TABLE><p>\n"+description+"<br><p>\n"+
                  "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n"+
                  "<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>Buy Now</B></FONT></TD></TR>\n"+
                  "</TABLE><p>\n"+
                  "<form action=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.StoreBuyNow\" method=POST>\n"+
                  "<input type=hidden name=userId value="+userId+">\n"+
                  "<input type=hidden name=itemId value="+itemId+">\n"+
                  "<input type=hidden name=maxQty value="+quantity+">\n");
    if (quantity > 1)
      result.append("<center><table><tr><td>Quantity:</td>\n"+
                    "<td><input type=text size=5 name=qty></td></tr></table></center>\n");
    else
      result.append("<input type=hidden name=qty value=1>\n");
    result.append("<p><input type=submit value=\"Buy now!\"></center><p>\n");
    return result.toString();
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
