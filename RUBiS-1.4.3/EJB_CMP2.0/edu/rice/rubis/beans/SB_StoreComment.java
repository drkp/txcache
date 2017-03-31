package edu.rice.rubis.beans;

import javax.ejb.*;
import java.rmi.*;

/**
 * This is the Remote Interface of the SB_StoreComment Bean
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.1
 */
public interface SB_StoreComment extends EJBObject, Remote {

 /**
   * Create a new comment and update the rating of the user.
   *
   * @param fromId id of the user posting the comment
   * @param toId id of the user who is the subject of the comment
   * @param itemId id of the item related to the comment
   * @param rating value of the rating for the user
   * @param comment text of the comment
   * @since 1.1
   */
  public void createComment(Integer fromId, Integer toId, Integer itemId, int rating, String comment) throws RemoteException;


}
