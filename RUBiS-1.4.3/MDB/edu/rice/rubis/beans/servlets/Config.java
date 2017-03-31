package edu.rice.rubis.beans.servlets;

/** 
 * This class contains the configuration for the servlets
 * like the path of HTML files, etc ...
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class Config
{

  /**
   * Creates a new <code>Config</code> instance.
   *
   */
  Config()
  {
  }

  /**
   * Return the TopicConnectionFactory name to lookup
   * JOnAS looks like: JTCF
   * JBoss looks like: RMIConnectionFactoryw
   */
  public static final String TopicConnectionFactoryName = "RMIConnectionFactory";

  /**
   * Return the prefix to add to the topic name at lookup time
   * JOnAS looks like: 
   * JBoss looks like: topic/
   */
  public static final String PrefixTopicName = "topic/";

  /**
   * Returns the path to the directory where the HTML header and footer are stored.
   */
  //public static final String HTMLFilesPath = "/users/cecchet/RUBiS/EJB_HTML";
  public static final String HTMLFilesPath = "/users/margueri/RUBiS/ejb_rubis_web";

  /**
   * Returns the context used by the web container for html files and servlets (this isthe name of the war file).
   * If no war file is used context is /EJB_HTML
   */
  public static final String context = "/ejb_rubis_web";


  /**
   * Return the UserTransaction name to look for since JBoss does not support full class names
   * JOnAS looks like: utx = (javax.transaction.UserTransaction)initialContext.lookup("java:comp/UserTransaction");
   * JBoss looks like: utx = (javax.transaction.UserTransaction)initialContext.lookup("UserTransaction");
   */
  public static final String UserTransaction = "UserTransaction";
}
