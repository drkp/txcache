package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Local Interface of the Region Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public interface RegionLocal extends EJBLocalObject {
  /**
   * Get region's id.
   *
   * @return region id
   */
  public Integer getId();

  /**
   * Get the region name.
   *
   * @return region name
   */
  public String getName();

  /**
   * Set region's name
   *
   * @param newName region name
   */
  public void setName(String newName);
}
