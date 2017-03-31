package edu.rice.rubis.beans;

/**
 * Category Primary Key class
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class CategoryPK implements java.io.Serializable {
  
  public Integer id;

  /**
   * Creates a new <code>CategoryPK</code> instance.
   *
   */
  public CategoryPK() {}
    
  /**
   * Creates a new <code>CategoryPK</code> instance.
   *
   * @param uniqueId an <code>Integer</code> value
   */
  public CategoryPK(Integer uniqueId)
  {
    id = uniqueId;
  }

  /**
   * Specific <code>hashCode</code> just returning the id.
   *
   * @return the hash code
   */
  public int hashCode() 
  {
    if (id == null)
      return 0;
    else
      return id.intValue();
  }
  /**
   * Specific <code>equals</code> method.
   *
   * @param other the <code>Object</code> to compare with
   * @return true if both objects have the same primary key
   */
  public boolean equals(Object other) 
  {
    boolean isEqual = false;
    if (other instanceof CategoryPK)
    {
      if (id == null)
        isEqual = (id == ((CategoryPK)other).id);
      else
        isEqual = (id.intValue() == ((CategoryPK)other).id.intValue());
    }
    return isEqual;
  }


}
