package edu.rice.rubis.beans;

import java.rmi.RemoteException;
import java.util.Collection;
import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

/** This is the local home interface of the Region Bean */

public interface RegionLocalHome extends EJBLocalHome {

  /**
   * This method is used to create a new Region Bean.
   *
   * @param name Region name
   *
   * @return pk primary key set to null
   */
  public RegionLocal create(String name) throws CreateException;


  /**
   * This method is used to retrieve a Region Bean from its primary key,
   * that is to say its id.
   *
   * @param id Region id (primary key)
   *
   * @return the Region if found else null
   */
  public RegionLocal findByPrimaryKey(RegionPK id) throws FinderException;


  /**
   * This method is used to retrieve a Region Bean from its name.
   *
   * @param regionName Region name
   *
   * @return the Region if found else null
   */
  public RegionLocal findByName(String regionName) throws FinderException;


  /**
   * This method is used to retrieve all categories from the database!
   *
   * @return List of all regions (eventually empty)
   */
  public Collection findAllRegions() throws FinderException;
}
