package edu.rice.rubis.beans;

/** 
 * This class contains the configuration for the MDB
 * like the topic connection factory name
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class BeanConfig
{

  /**
   * Creates a new <code>BeanConfig</code> instance.
   *
   */
  BeanConfig()
  {
  }

  /**
   * Return the TopicConnectionFactory name to lookup
   * JOnAS looks like: JTCF
   * JBoss looks like: RMIConnectionFactory
   */
  public static final String TopicConnectionFactoryName = "RMIConnectionFactory";

  /**
   * Return the prefix to add to the topic name at lookup time
   * JOnAS looks like: 
   * JBoss looks like: topic/
   */
  public static final String PrefixTopicName = "topic/";

  /**
   * Return the UserTransaction name to look for since JBoss does not support full class names
   * JOnAS looks like: utx = (javax.transaction.UserTransaction)initialContext.lookup("java:comp/UserTransaction");
   * JBoss looks like: utx = (javax.transaction.UserTransaction)initialContext.lookup("UserTransaction");
   */
  public static final String UserTransaction = "UserTransaction";

  /**
   * Returns the context used by the web container for html files and servlets (this is the name of the war file).
   * If no war file is used context is /EJB_HTML
   */
  public static final String context = "/ejb_rubis_web";
}
