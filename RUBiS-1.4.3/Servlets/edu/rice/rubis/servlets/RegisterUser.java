package edu.rice.rubis.servlets;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

/** 
 * Add a new user in the database 
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * @version 1.0
 */
public class RegisterUser extends RubisHttpServlet
{
  private UserTransaction utx = null;
  

  public int getPoolSize()
  {
    return Config.RegisterUserPoolSize;
  }

/**
 * Close both statement and connection.
 */
  private void closeConnection(PreparedStatement stmt, Connection conn)
  {
    try
    {
      if (stmt != null)
        stmt.close(); // close statement
      if (conn != null)
        releaseConnection(conn);
    }
    catch (Exception ignore)
    {
    }
  }

/**
 * Display an error message.
 * @param errorMsg the error message value
 */
  private void printError(String errorMsg, ServletPrinter sp)
  {
    sp.printHTMLheader("RUBiS ERROR: Register user");
    sp.printHTML(
      "<h2>Your registration has not been processed due to the following error :</h2><br>");
    sp.printHTML(errorMsg);
    sp.printHTMLfooter();


  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    
    String firstname = null,
      lastname = null,
      nickname = null,
      email = null,
      password = null;
    int regionId;
    int userId;
    String creationDate, region;

    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "RegisterUser");

    String value = request.getParameter("firstname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a first name!<br>", sp);
      return;
    }
    else
      firstname = value;

    value = request.getParameter("lastname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a last name!<br>", sp);
      return;
    }
    else
      lastname = value;

    value = request.getParameter("nickname");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a nick name!<br>", sp);
      return;
    }
    else
      nickname = value;

    value = request.getParameter("email");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide an email address!<br>", sp);
      return;
    }
    else
      email = value;

    value = request.getParameter("password");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a password!<br>", sp);
      return;
    }
    else
      password = value;

    value = request.getParameter("region");
    if ((value == null) || (value.equals("")))
    {
      printError("You must provide a valid region!<br>", sp);
      return;
    }
    else
    {
      region = value;
      
      try
      {
        conn = getConnection();
        stmt = conn.prepareStatement("SELECT id FROM regions WHERE name=?");
        stmt.setString(1, region);
        ResultSet rs = stmt.executeQuery();
        if (!rs.first())
        {
          printError(
            " Region " + value + " does not exist in the database!<br>", sp);
            closeConnection(stmt, conn);
          return;
        }
        regionId = rs.getInt("id");
        stmt.close();
      }
      catch (SQLException e)
      {
        printError("Failed to execute Query for region: " + e, sp);
        closeConnection(stmt, conn);
        return;
      }
    }
    // Try to create a new user
    try
    {
      stmt =
        conn.prepareStatement("SELECT nickname FROM users WHERE nickname=?");
      stmt.setString(1, nickname);
      ResultSet rs = stmt.executeQuery();
      if (rs.first())
      {
        printError("The nickname you have choosen is already taken by someone else. Please choose a new nickname.<br>", sp);
        closeConnection(stmt, conn);
        return;
      }
      stmt.close();
    }
    catch (SQLException e)
    {
      printError("Failed to execute Query to check the nickname: " + e, sp);
      closeConnection(stmt, conn);
      return;
    }
    try
    {
      String now = TimeManagement.currentDateToString();
      stmt =
        conn.prepareStatement(
          "INSERT INTO users VALUES (NULL, \""
            + firstname
            + "\", \""
            + lastname
            + "\", \""
            + nickname
            + "\", \""
            + password
            + "\", \""
            + email
            + "\", 0, 0,\""
            + now
            + "\", "
            + regionId
            + ")");
      stmt.executeUpdate();
      stmt.close();
    }
    catch (SQLException e)
    {
      printError(
        "RUBiS internal error: User registration failed (got exception: "
          + e
          + ")<br>", sp);
      closeConnection(stmt, conn);
      return;
    }
    try
    {
      stmt =
        conn.prepareStatement(
          "SELECT id, creation_date FROM users WHERE nickname=?");
      stmt.setString(1, nickname);
      ResultSet urs = stmt.executeQuery();
      if (!urs.first())
      {
        printError("This user does not exist in the database.", sp);
        closeConnection(stmt, conn);
        return;
      }
      userId = urs.getInt("id");
      creationDate = urs.getString("creation_date");
    }
    catch (SQLException e)
    {
      printError("Failed to execute Query for user: " + e, sp);
      closeConnection(stmt, conn);
      return;
    }


    sp.printHTMLheader("RUBiS: Welcome to " + nickname);
    sp.printHTML(
      "<h2>Your registration has been processed successfully</h2><br>");
    sp.printHTML("<h3>Welcome " + nickname + "</h3>");
    sp.printHTML("RUBiS has stored the following information about you:<br>");
    sp.printHTML("First Name : " + firstname + "<br>");
    sp.printHTML("Last Name  : " + lastname + "<br>");
    sp.printHTML("Nick Name  : " + nickname + "<br>");
    sp.printHTML("Email      : " + email + "<br>");
    sp.printHTML("Password   : " + password + "<br>");
    sp.printHTML("Region     : " + region + "<br>");
    sp.printHTML(
      "<br>The following information has been automatically generated by RUBiS:<br>");
    sp.printHTML("User id       :" + userId + "<br>");
    sp.printHTML("Creation date :" + creationDate + "<br>");

    sp.printHTMLfooter();
    closeConnection(stmt, conn);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    doGet(request, response);
  }

  /**
   * Clean up the connection pool.
   */
  public void destroy()
  {
    super.destroy();
  }
}
