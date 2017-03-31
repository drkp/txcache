package edu.rice.rubis.beans.servlets;

import java.io.IOException;

import javax.ejb.FinderException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import edu.rice.rubis.beans.Region;
import edu.rice.rubis.beans.RegionHome;
import edu.rice.rubis.beans.User;
import edu.rice.rubis.beans.UserHome;

/** This servlet register a new user in the database and display
 * the result of the transaction.
 * It must be called this way :
 * <pre>
 * http://..../RegisterUser?firstname=aa&lastname=bb&nickname=cc&email=dd&password=ee&region=ff
 *   where: aa is the user first name
 *          bb is the user last name
 *          cc is the user nick name (login name)
 *          dd is the email address of the user
 *          ee is the user password
 *          ff is the identifier of the region where the user lives
 * </pre>
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */

public class RegisterUser extends HttpServlet
{
  

  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Register user");
    sp.printHTML("<h2>Your registration has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();
  }

  /**
   * Describe <code>doGet</code> method here.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    UserTransaction utx = null;
    ServletPrinter sp = null;
    String firstname=null, lastname=null, nickname=null, email=null, password=null;
    int    regionId = 0;
    int    userId;
    String creationDate;

    sp = new ServletPrinter(response, "RegisterUser");
      
    Context initialContext = null;
    try
    {
      initialContext = new InitialContext();
    } 
    catch (Exception e) 
    {
      printError("Cannot get initial context for JNDI: " + e+"<br>", sp);
      return ;
    }

    String value = request.getParameter("firstname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a first name!<br>", sp);
      return ;
    }
    else
      firstname = value;

    value = request.getParameter("lastname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a last name!<br>", sp);
      return ;
    }
    else
      lastname = value;

    value = request.getParameter("nickname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a nick name!<br>", sp);
      return ;
    }
    else
      nickname = value;

    value = request.getParameter("email");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide an email address!<br>", sp);
      return ;
    }
    else
      email = value;

    value = request.getParameter("password");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a password!<br>", sp);
      return ;
    }
    else
      password = value;


    value = request.getParameter("region");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a valid region!<br>", sp);
      return ;
    }
    else
    {
      RegionHome regionHome;
      try 
      {
        // Connecting to Home thru JNDI
        regionHome = (RegionHome)PortableRemoteObject.narrow(initialContext.lookup("RegionHome"), RegionHome.class);
      } 
      catch (Exception e)
      {
        printError("RUBiS internal error: Cannot lookup Region: " +e+"<br>\n", sp);
        return ;
      }
      try
      {
        Region region = regionHome.findByName(value);
        regionId = region.getId().intValue();
      }
      catch (Exception e)
      {
        printError(" Region "+value+" does not exist in the database!<br>(got exception: " +e+")<br>\n", sp);
        return ;
      }
    }

    // Try to create a new user
    UserHome userHome;
    User     user;
    try 
    {
      // Connecting to Home thru JNDI
      userHome = (UserHome)PortableRemoteObject.narrow(initialContext.lookup("UserHome"),
                                                       UserHome.class);
    } 
    catch (Exception e)
    {
      printError("RUBiS internal error: Cannot lookup User: " +e+"<br>", sp);
      return ;
    }
    try
    {
      user = userHome.findByNickName(nickname);
      /* If an exception has not be thrown at this point, it means that
         the nickname already exists. */
      printError("The nickname you have choosen is already taken by someone else. Please choose a new nickname.<br>", sp);
      return ;
    }
    catch (FinderException fe)
    {
      try
      {
        user = userHome.create(firstname, lastname, nickname, email, password, new Integer(regionId));
        user = userHome.findByNickName(nickname);
        userId = user.getId().intValue();
        creationDate = user.getCreationDate();
      }
      catch (Exception e)
      {
        printError("RUBiS internal error: User registration failed (got exception: " +e+")<br>", sp);
        return ;
      }
    }

    sp.printHTMLheader("RUBiS: Welcome to "+nickname);
    sp.printHTML("<h2>Your registration has been processed successfully</h2><br>\n");
    sp.printHTML("<h3>Welcome "+nickname+"</h3>\n");
    sp.printHTML("RUBiS has stored the following information about you:<br>\n");
    sp.printHTML("First Name : "+firstname+"<br>\n");
    sp.printHTML("Last Name  : "+lastname+"<br>\n");
    sp.printHTML("Nick Name  : "+nickname+"<br>\n");
    sp.printHTML("Email      : "+email+"<br>\n");
    sp.printHTML("Password   : "+password+"<br>\n");
    sp.printHTML("Region     : "+value+"<br>\n"); // Note that it is really dirty to reuse value here !!
    sp.printHTML("<br>The following information has been automatically generated by RUBiS:<br>\n");
    sp.printHTML("User id       :"+userId+"<br>\n");
    sp.printHTML("Creation date :"+creationDate+"<br>\n");
    sp.printHTML("Rating        :"+user.getRating()+"<br>\n");
    sp.printHTML("Balance       :"+user.getBalance()+"<br>\n");
      
    sp.printHTMLfooter();
  }
    
 
  /**
   * Call the <code>doGet</code> method here.
   *
   * @param request a <code>HttpServletRequest</code> value
   * @param response a <code>HttpServletResponse</code> value
   * @exception IOException if an error occurs
   * @exception ServletException if an error occurs
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    doGet(request, response);
  }
}
