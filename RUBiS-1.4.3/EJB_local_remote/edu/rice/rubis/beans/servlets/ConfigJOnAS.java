package edu.rice.rubis.beans.servlets;

/** 
 * JOnAS version.
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
   * Returns the context used by the web container for html files and servlets (this isthe name of the war file).
   */
  public static final String context = "/ejb_rubis_web";
  
  /**
   * Returns the path to the directory where the HTML header and footer are stored.
   */
  public static final String HTMLFilesPath = "/users/margueri/RUBiS/ejb_rubis_web";

  /**
   * Return the UserTransaction name to look for since JBoss does not support full class names
   * JOnAS looks like: utx = (javax.transaction.UserTransaction)initialContext.lookup("java:comp/UserTransaction");
   * JBoss looks like: utx = (javax.transaction.UserTransaction)initialContext.lookup("UserTransaction");
   */
  public static final String UserTransaction = "java:comp/UserTransaction";
}
