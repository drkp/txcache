package edu.rice.rubis.beans;

import java.rmi.*;
import javax.ejb.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import java.util.GregorianCalendar;

/**
 * ItemBean is an entity bean with "container managed persistence".
 * The state of an instance is stored into a relational database.
 * The following table should exist:<p>
 * <pre>
 * CREATE TABLE items (
 *    id            INTEGER UNSIGNED NOT NULL UNIQUE,
 *    name          VARCHAR(100),
 *    description   TEXT,
 *    initial_price FLOAT UNSIGNED NOT NULL,
 *    quantity      INTEGER UNSIGNED NOT NULL,
 *    reserve_price FLOAT UNSIGNED DEFAULT 0,
 *    buy_now       FLOAT UNSIGNED DEFAULT 0,
 *    nb_of_bids    INTEGER UNSIGNED DEFAULT 0,
 *    max_bid       FLOAT UNSIGNED DEFAULT 0,
 *    start_date    DATETIME,
 *    end_date      DATETIME,
 *    seller        INTEGER,
 *    category      INTEGER,
 *    PRIMARY KEY(id),
 *    INDEX seller_id (seller),
 *    INDEX category_id (category)
 * );
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */

public class ItemBean implements EntityBean 
{
  private  EntityContext entityContext;
  private transient boolean isDirty; // used for the isModified function

  /* Class member variables */

  public Integer id;
  public String  name;
  public String  description;
  public float   initialPrice;
  public int     quantity;
  public float   reservePrice;
  public float   buyNow;
  public int     nbOfBids;
  public float   maxBid;
  public String  startDate;
  public String  endDate;
  public Integer sellerId;
  public Integer categoryId;


  /**
   * Get item id.
   *
   * @return item id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Integer getId() throws RemoteException
  {
    return id;
  }

  /**
   * Get item name. This description is usually a short description of the item.
   *
   * @return item name
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getName() throws RemoteException
  {
    return name;
  }

  /**
   * Get item description . This is usually an HTML file describing the item.
   *
   * @return item description
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getDescription() throws RemoteException
  {
    return description;
  }

  /**
   * Get item initial price set by the seller.
   *
   * @return item initial price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getInitialPrice() throws RemoteException
  {
    return initialPrice;
  }

  /**
   * Get how many of this item are to be sold.
   *
   * @return item quantity
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public int getQuantity() throws RemoteException
  {
    return quantity;
  }

  /**
   * Get item reserve price set by the seller. The seller can refuse to sell if reserve price is not reached.
   *
   * @return item reserve price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getReservePrice() throws RemoteException
  {
    return reservePrice;
  }

  /**
   * Get item Buy Now price set by the seller. A user can directly by the item at this price (no auction).
   *
   * @return item Buy Now price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public float getBuyNow() throws RemoteException
  {
    return buyNow;
  }

  /**
   * Get item maximum bid (if any) for this item. This value should be the same as doing <pre>SELECT MAX(bid) FROM bids WHERE item_id=?</pre>
   *
   * @return current maximum bid or 0 if no bid
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public float getMaxBid() throws RemoteException
  {
    return maxBid;
  }

  /**
   * Get number of bids for this item. This value should be the same as doing <pre>SELECT COUNT(*) FROM bids WHERE item_id=?</pre>
   *
   * @return number of bids
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public int getNbOfBids() throws RemoteException
  {
    return nbOfBids;
  }

  /**
   * Start date of the auction in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return start date of the auction
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getStartDate() throws RemoteException
  {
    return startDate;
  }

  /**
   * End date of the auction in the format 'YYYY-MM-DD hh:mm:ss'
   *
   * @return end date of the auction
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getEndDate() throws RemoteException
  {
    return endDate;
  }

  /**
   * Give the user id of the seller
   *
   * @return seller's user id
   * @since 1.0
   * @exception RemoteException if an error occurs
   */
  public Integer getSellerId() throws RemoteException
  {
    return sellerId;
  }


  /**
   * Give the category id of the item
   *
   * @return item's category id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public Integer getCategoryId() throws RemoteException
  {
    return categoryId;
  }

  
  /**
   * Get the seller's nickname by finding the Bean corresponding
   * to the user. 
   *
   * @return nickname
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getSellerNickname() throws RemoteException
  {
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      System.err.print("Cannot get initial context for JNDI: " + e);
      return null;
    }

    // Try to find the user nick name corresponding to the sellerId
    UserHome uHome;
    try 
    {
      uHome = (UserHome)PortableRemoteObject.narrow(initialContext.lookup("UserHome"),
                                                    UserHome.class);
    } 
    catch (Exception e)
    {
      System.err.print("Cannot lookup User: " +e);
      return null;
    }
    try
    {
      User u = uHome.findByPrimaryKey(new UserPK(sellerId));
      return u.getNickName();
    }
    catch (Exception e)
    {
      System.err.print("This user does not exist (got exception: " +e+")<br>");
      return null;
    }
  }
  

  /**
   * Get the category name by finding the Bean corresponding to the category Id.
   *
   * @return category name
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String getCategoryName() throws RemoteException
  {
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      System.err.print("Cannot get initial context for JNDI: " + e);
      return null;
    }

    // Try to find the CategoryName corresponding to the categoryId
    CategoryHome cHome;
    try 
    {
      cHome = (CategoryHome)PortableRemoteObject.narrow(initialContext.lookup("CategoryHome"),
                                                        CategoryHome.class);
    } 
    catch (Exception e)
    {
      System.err.print("Cannot lookup Category: " +e);
      return null;
    }
    try
    {
      Category c = cHome.findByPrimaryKey(new CategoryPK(id));
      return c.getName();
    }
    catch (Exception e)
    {
      System.err.print("This category does not exist (got exception: " +e+")<br>");
      return null;
    }
  }


  /**
   * Set a new item name
   *
   * @param newName item name
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setName(String newName) throws RemoteException 
  {
    name = newName;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new item description
   *
   * @param newDescription item description
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setDescription(String newDescription) throws RemoteException 
  {
    description = newDescription;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new initial price for the item
   *
   * @param newInitialPrice item initial price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setInitialPrice(float newInitialPrice) throws RemoteException
  {
    initialPrice = newInitialPrice;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new item quantity
   *
   * @param qty item quantity
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setQuantity(int qty) throws RemoteException
  {
    quantity = qty;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new reserve price for the item
   *
   * @param newReservePrice item reserve price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setReservePrice(float newReservePrice) throws RemoteException
  {
    reservePrice = newReservePrice;
  }

  /**
   * Set a new Buy Now price for the item
   *
   * @param newBuyNow item Buy Now price
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setBuyNow(float newBuyNow) throws RemoteException
  {
    buyNow = newBuyNow;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set item maximum bid. This function checks if newMaxBid is greater
   * than current maxBid and only updates the value in this case.
   *
   * @param newMaxBid new maximum bid
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public void setMaxBid(float newMaxBid) throws RemoteException
  {
    if (newMaxBid > maxBid)
    {
      maxBid = newMaxBid;
      isDirty = true; // the bean content has been modified
    }
  }

  /**
   * Set the number of bids for this item
   *
   * @param newNbOfBids new number of bids
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public void setNbOfBids(int newNbOfBids) throws RemoteException
  {
    nbOfBids = newNbOfBids;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Add one bid for this item
   *
   * @exception RemoteException if an error occurs
   * @since 1.1
   */
  public void addOneBid() throws RemoteException
  {
    nbOfBids++;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new beginning date for the auction
   *
   * @param newDate auction new beginning date
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setStartDate(String newDate) throws RemoteException
  {
    startDate = newDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new ending date for the auction
   *
   * @param newDate auction new ending date
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setEndDate(String newDate) throws RemoteException
  {
    endDate = newDate;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new seller identifier. This id must match
   * the primary key of the users table.
   *
   * @param id seller id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setSellerId(Integer id) throws RemoteException
  {
    sellerId = id;
    isDirty = true; // the bean content has been modified
  }

  /**
   * Set a new category identifier. This id must match
   * the primary key of the category table.
   *
   * @param id category id
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public void setCategoryId(Integer id) throws RemoteException
  {
    categoryId = id;
    isDirty = true; // the bean content has been modified
  }


  /**
   * This method is used to create a new Item Bean.
   *
   * @param itemName short item designation
   * @param itemDescription long item description, usually an HTML file
   * @param itemInitialPrice initial price fixed by the seller
   * @param itemQuantity number to sell (of this item)
   * @param itemReservePrice reserve price (minimum price the seller really wants to sell)
   * @param itemBuyNow price if a user wants to buy the item immediatly
   * @param duration duration of the auction in days (start date is when the method is called and end date is computed according to the duration)
   * @param itemSellerId seller id, must match the primary key of table users
   * @param itemCategoryId category id, must match the primary key of table categories
   *
   * @return pk primary key set to null
   *
   * @exception CreateException if an error occurs
   * @exception RemoteException if an error occurs
   * @exception RemoveException if an error occurs
   * @since 1.0
   */
  public ItemPK ejbCreate(String itemName, String itemDescription, float itemInitialPrice,
                          int itemQuantity, float itemReservePrice, float itemBuyNow, int duration,
                          Integer itemSellerId, Integer itemCategoryId) throws CreateException, RemoteException, RemoveException
  {
    GregorianCalendar start = new GregorianCalendar();
    // Connecting to IDManager Home interface thru JNDI
      IDManagerHome home = null;
      IDManager idManager = null;
      
      try 
      {
        InitialContext initialContext = new InitialContext();
        home = (IDManagerHome)PortableRemoteObject.narrow(initialContext.lookup(
               "java:comp/env/ejb/IDManager"), IDManagerHome.class);
      } 
      catch (Exception e)
      {
        throw new EJBException("Cannot lookup IDManager: " +e);
      }
     try 
      {
        IDManagerPK idPK = new IDManagerPK();
        idManager = home.findByPrimaryKey(idPK);
        id = idManager.getNextItemID();
        name         = itemName;
        description  = itemDescription;
        initialPrice = itemInitialPrice;
        quantity     = itemQuantity;
        reservePrice = itemReservePrice;
        buyNow       = itemBuyNow;
        sellerId     = itemSellerId;
        categoryId   = itemCategoryId;
        nbOfBids     = 0;
        maxBid       = 0;
        startDate    = TimeManagement.dateToString(start);
        endDate      = TimeManagement.dateToString(TimeManagement.addDays(start, duration));
      } 
     catch (Exception e)
     {
        throw new EJBException("Cannot create item: " +e);
      }
    return null;
  }

  /** This method does currently nothing */
  public void ejbPostCreate(String itemName, String itemDescription, float itemInitialPrice,
			    int itemQuantity, float itemReservePrice, float itemBuyNow, int duration,
			    Integer itemSellerId, Integer itemCategoryId) {}

  /** This method is empty because persistence is managed by the container */
  public void ejbActivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbPassivate() throws RemoteException {}
  /** This method is empty because persistence is managed by the container */
  public void ejbRemove() throws RemoteException, RemoveException {}

  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbStore() throws RemoteException
  {
    isDirty = false;
  }
  /** Persistence is managed by the container and the bean
      becomes up to date */
  public void ejbLoad() throws RemoteException
  {
    isDirty = false;
  }

  /**
   * Sets the associated entity context. The container invokes this method 
   *  on an instance after the instance has been created. 
   * 
   * This method is called in an unspecified transaction context. 
   * 
   * @param context An EntityContext interface for the instance. The instance should 
   *                store the reference to the context in an instance variable. 
   * @exception EJBException  Thrown by the method to indicate a failure 
   *                          caused by a system-level error.
   * @exception RemoteException - This exception is defined in the method signature
   *                           to provide backward compatibility for enterprise beans
   *                           written for the EJB 1.0 specification. 
   *                           Enterprise beans written for the EJB 1.1 and 
   *                           higher specification should throw the javax.ejb.EJBException 
   *                           instead of this exception. 
   */
  public void setEntityContext(EntityContext context) throws RemoteException
  {
    entityContext = context;
  }

  /**
   * Unsets the associated entity context. The container calls this method 
   *  before removing the instance. This is the last method that the container 
   *  invokes on the instance. The Java garbage collector will eventually invoke 
   *  the finalize() method on the instance. 
   *
   * This method is called in an unspecified transaction context. 
   * 
   * @exception EJBException  Thrown by the method to indicate a failure 
   *                          caused by a system-level error.
   * @exception RemoteException - This exception is defined in the method signature
   *                           to provide backward compatibility for enterprise beans
   *                           written for the EJB 1.0 specification. 
   *                           Enterprise beans written for the EJB 1.1 and 
   *                           higher specification should throw the javax.ejb.EJBException 
   *                           instead of this exception.
   */
  public void unsetEntityContext() throws RemoteException
  {
    entityContext = null;
  }

  /**
   * Returns true if the beans has been modified.
   * It prevents the EJB server from reloading a bean
   * that has not been modified.
   *
   * @return a <code>boolean</code> value
   */
  public boolean isModified() 
  {
    return isDirty;
  }


  /**
   * Display item information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItem() throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+
      "<TD>"+maxBid+
      "<TD>"+nbOfBids+
      "<TD>"+endDate+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutBidAuth?itemId="+id+"\"><IMG SRC=\"/EJB_HTML/bid_now.jpg\" height=22 width=90></a>\n";
  }

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printUserBoughtItem(int qty) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+"</a>\n"+
      "<TD>"+qty+"\n"+"<TD>"+buyNow+"\n"+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+getSellerNickname()+"</a>\n";
  }

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code (Warning last link must be completed by servlet)
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemUserHasBidOn(float bidMaxBid) throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+
      "<TD>"+initialPrice+"<TD>"+maxBid+"<TD>"+bidMaxBid+"<TD>"+quantity+"<TD>"+startDate+"<TD>"+endDate+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+getSellerNickname()+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.PutBid?itemId="+id;
  }


  /**
   * Display item information as an HTML table row
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printSell() throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+
      "<TD>"+initialPrice+"<TD>"+maxBid+"<TD>"+quantity+"<TD>"+reservePrice+"<TD>"+buyNow+"<TD>"+startDate+"<TD>"+endDate+"\n";
  }

  /**
   * Display item information for the AboutMe servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printUserWonItem() throws RemoteException
  {
    return "<TR><TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewItem?itemId="+id+"\">"+name+"</a>\n"+
      "<TD>"+maxBid+"\n"+
      "<TD><a href=\""+BeanConfig.context+"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+getSellerNickname()+"</a>\n";
  }

  /**
   * Display item information for the Buy Now servlet
   *
   * @return a <code>String</code> containing HTML code
   * @exception RemoteException if an error occurs
   * @since 1.0
   */
  public String printItemDescriptionToBuyNow(int userId) throws RemoteException
  {
    String result = "<TABLE>\n"+"<TR><TD>Quantity<TD><b><BIG>"+quantity+"</BIG></b>\n"+
      "<TR><TD>Seller<TD><a href=\"/servlet/edu.rice.rubis.beans.servlets.ViewUserInfo?userId="+sellerId+"\">"+
      getSellerNickname()+"</a> (<a href=\"/servlet/edu.rice.rubis.beans.servlets.PutCommentAuth?to="+sellerId+"&itemId="+id+"\">Leave a comment on this user</a>)\n"+
      "<TR><TD>Started<TD>"+startDate+"\n"+"<TR><TD>Ends<TD>"+endDate+"\n"+
      "</TABLE>"+
      "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n"+
      "<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>Item description</B></FONT></TD></TR>\n"+
      "</TABLE><p>\n"+description+"<br><p>\n"+
      "<TABLE width=\"100%\" bgcolor=\"#CCCCFF\">\n"+
      "<TR><TD align=\"center\" width=\"100%\"><FONT size=\"4\" color=\"#000000\"><B>Buy Now</B></FONT></TD></TR>\n"+
      "</TABLE><p>\n"+
      "<form action=\"/servlet/edu.rice.rubis.beans.servlets.StoreBuyNow\" method=POST>\n"+
      "<input type=hidden name=userId value="+userId+">\n"+
      "<input type=hidden name=itemId value="+id+">\n"+
      "<input type=hidden name=maxQty value="+quantity+">\n";
    if (quantity > 1)
      result = result + "<center><table><tr><td>Quantity:</td>\n"+
        "<td><input type=text size=5 name=qty></td></tr></table></center>\n";
    else
      result = result + "<input type=hidden name=qty value=1>\n";
    return result;
  }
}
