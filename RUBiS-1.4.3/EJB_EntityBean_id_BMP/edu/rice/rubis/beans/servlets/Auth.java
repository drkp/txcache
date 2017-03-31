package edu.rice.rubis.beans.servlets;

import javax.naming.Context;
import javax.rmi.PortableRemoteObject;
import javax.servlet.http.HttpServlet;

import edu.rice.rubis.beans.User;
import edu.rice.rubis.beans.UserHome;

/**
 * This class is not a servlet but it provides user authentication services to servlets.
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class Auth extends HttpServlet
{

  private Context servletContext;
  private ServletPrinter sp;

  /**
   * Creates a new <code>Auth</code> instance.
   *
   * @param context a <code>Context</code> value
   * @param printer a <code>ServletPrinter</code> value
   */
  public Auth(Context context, ServletPrinter printer)
  {
    servletContext = context;
    sp = printer;
  }

  /**
   * Describe <code>authenticate</code> method here.
   *
   * @param name user nick name
   * @param password user password
   * @return an <code>int</code> value corresponding to the user id or -1 on error
   */
  public int authenticate(String name, String password)
  {
    int userId = -1;

    // Connecting to user Home interface thru JNDI
    UserHome userHome = null;
    try
    {
      userHome =
        (UserHome) PortableRemoteObject.narrow(
          servletContext.lookup("UserHome"),
          UserHome.class);
    }
    catch (Exception e)
    {
      sp.printHTML("Cannot lookup User: " + e + "<br>");
      return userId;
    }
    // get the User ID
    try
    {
      User user = userHome.findByNickName(name);
      String pwd = user.getPassword();
      if (pwd.compareTo(password) == 0)
      {
        userId = user.getId().intValue();
      }
    }
    catch (Exception e)
    {
      sp.printHTML(
        " User "
          + name
          + " does not exist in the database!<br>(got exception: "
          + e
          + ")<br>");
      return userId;
    }
    return userId;
  }

}
