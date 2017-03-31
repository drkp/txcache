package edu.rice.rubis.beans;

/** 
 * This class contains the configuration for the beans
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
   * Returns the context used by the web container for html files and servlets (this isthe name of the war file).
   */
  public static final String context = "/ejb_rubis_web";
}
