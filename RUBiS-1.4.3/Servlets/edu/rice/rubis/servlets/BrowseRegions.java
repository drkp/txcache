package edu.rice.rubis.servlets;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Builds the html page with the list of all region in the database */
public class BrowseRegions extends RubisHttpServlet
{
 


  public int getPoolSize()
  {
    return Config.BrowseRegionsPoolSize;
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
 * Get the list of regions from the database
 */
  private void regionList(ServletPrinter sp)
  {
    PreparedStatement stmt = null;
    Connection conn = null;
    String regionName;
    ResultSet rs = null;

    // get the list of regions
    try
    {
      conn = getConnection();

      stmt = conn.prepareStatement("SELECT name, id FROM regions");
      rs = stmt.executeQuery();
    }
    catch (Exception e)
    {
      sp.printHTML("Failed to executeQuery for the list of regions" + e);
      closeConnection(stmt, conn);
      return;
    }
    try
    {
      if (!rs.first())
      {
        sp.printHTML(
          "<h2>Sorry, but there is no region available at this time. Database table is empty</h2><br>");
        closeConnection(stmt, conn);
        return;
      }
      else
        sp.printHTML("<h2>Currently available regions</h2><br>");

      do
      {
        regionName = rs.getString("name");
        sp.printRegion(regionName);
      }
      while (rs.next());
      closeConnection(stmt, conn);

    }
    catch (Exception e)
    {
      sp.printHTML("Exception getting region list: " + e + "<br>");    
      closeConnection(stmt, conn);
    }
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException
  {
    ServletPrinter sp = null;
    sp = new ServletPrinter(response, "BrowseRegions");
    sp.printHTMLheader("RUBiS: Available regions");

    regionList(sp);
    sp.printHTMLfooter();
  }

  /**
   * Clean up the connection pool.
   */
  public void destroy()
  {
    super.destroy();
  }

}
