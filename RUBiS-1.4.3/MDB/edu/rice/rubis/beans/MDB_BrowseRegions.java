package edu.rice.rubis.beans;

import java.net.URLEncoder;
import javax.naming.*;
import javax.jms.*;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.EJBException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import javax.naming.InitialContext;

/**
 * This message driven bean gets the list of regions from the database. 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class MDB_BrowseRegions implements MessageDrivenBean, MessageListener 
{
  private DataSource dataSource;
  private MessageDrivenContext messageDrivenContext;
  private TopicConnectionFactory connectionFactory;
  private TopicConnection connection;
  private Topic topic;
  private TopicSession session;
  private TopicPublisher replier;
  private Context initialContext = null;


  public MDB_BrowseRegions()
  {

  }


  public void onMessage(Message request)
  {
    try
    {
      String correlationID = request.getJMSCorrelationID();

      // get the list of regions
      String html = listRegions();

      // send the reply
      TemporaryTopic temporaryTopic = (TemporaryTopic) request.getJMSReplyTo();
      if (temporaryTopic != null)
      {
        // Retrieve the connection factory
        connectionFactory = (TopicConnectionFactory) initialContext.lookup(BeanConfig.TopicConnectionFactoryName);
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
      throw new EJBException("Message traitment failed: " +e);
    }
  }

  /**
   * Retrieve the list of regions from the database.
   * @return a string in html format
   * @since 1.1
   */
  public String listRegions() throws Exception
  {
    Connection        conn     = null;
    PreparedStatement stmt     = null;
    StringBuffer      htmlPage = new StringBuffer();

    try 
    {
      conn = dataSource.getConnection();
      stmt = conn.prepareStatement("SELECT name FROM regions");
      ResultSet rs = stmt.executeQuery();
      // Build the html page
      String regionName;
      while(rs.next())
      {
        regionName = rs.getString("name");
        htmlPage.append(printRegion(regionName));
      }
      stmt.close();	// close statement
      conn.close();	// release connection
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
      throw new Exception("Failed to get regions " +e);
    }
    return htmlPage.toString();
  }

  /** 
   * Region related printed functions
   *
   * @param name the name of the region to display
   * @return a string in html format
   * @since 1.1
   */

  public String printRegion(String name)
  {
    return "<a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.BrowseCategories?region="+URLEncoder.encode(name)+"\">"+name+"</a><br>\n";

  }

  // ======================== MDB related methods ============================

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
