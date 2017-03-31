/*
 * RUBiS
 * Copyright (C) 2002, 2003, 2004 French National Institute For Research In Computer
 * Science And Control (INRIA).
 * Contact: jmob@objectweb.org
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2.1 of the License, or any later
 * version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * Initial developer(s): Emmanuel Cecchet, Julie Marguerite
 * Contributor(s): Jeremy Philippe, Niraj Tolia
 */
 
package edu.rice.rubis.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.GregorianCalendar;
import java.net.URL;

import edu.rice.rubis.client.TimeManagement;
import edu.rice.rubis.client.RUBiSProperties;
import edu.rice.rubis.client.Stats;
import edu.rice.rubis.client.TransitionTable;
import edu.rice.rubis.client.URLGenerator;
import edu.rice.rubis.client.UserSession;

/**
 * RUBiS client emulator. 
 * This class plays random user sessions emulating a Web browser.
 *
 * @author <a href="mailto:cecchet@rice.edu">Emmanuel Cecchet</a> and <a href="mailto:julie.marguerite@inrialpes.fr">Julie Marguerite</a>
 * 
 * @version 1.0
 */
public class ClientEmulator
{
  private static RUBiSProperties rubis = null; // access to rubis.properties file
  private URLGenerator urlGen = null;
  // URL generator corresponding to the version to be used (PHP, EJB or Servlets)
  private static float slowdownFactor = 0;
  private static boolean endOfSimulation = false;
  private static PrintStream origStderr;

  /**
   * Creates a new <code>ClientEmulator</code> instance.
   * The program is stopped on any error reading the configuration files.
   */
  public ClientEmulator(String propertiesFileName)
  {
    // Initialization, check that all files are ok
    rubis = new RUBiSProperties(propertiesFileName);
    urlGen = rubis.checkPropertiesFileAndGetURLGenerator();
    if (urlGen == null)
      Runtime.getRuntime().exit(1);
    // Check that the transition table is ok and print it
    TransitionTable transition =
      new TransitionTable(
        rubis.getNbOfColumns(),
        rubis.getNbOfRows(),
        null,
        rubis.useTPCWThinkTime());
    if (!transition.ReadExcelTextFile(rubis.getTransitionTable()))
      Runtime.getRuntime().exit(1);
    else
      transition.displayMatrix();
  }

  /**
   * Updates the slowdown factor.
   *
   * @param newValue new slowdown value
   */
  private synchronized void setSlowDownFactor(float newValue)
  {
    System.out.println("Setting slowdown factor to " + newValue + "<p>");
    slowdownFactor = newValue;
  }

  /**
   * Get the slowdown factor corresponding to current ramp (up, session or down).
   *
   * @return slowdown factor of current ramp
   */
  public static synchronized float getSlowDownFactor()
  {
    return slowdownFactor;
  }

  /**
   * Set the end of the current simulation
   */
  private static void setEndOfSimulation()
  {
    endOfSimulation = true;
  }

  /**
   * True if end of simulation has been reached.
   * @return true if end of simulation
   */
  public static boolean isEndOfSimulation()
  {
    return endOfSimulation;
  }

    // Austin
    private static java.util.concurrent.atomic.AtomicInteger nTransactions =
      new java.util.concurrent.atomic.AtomicInteger();
    public static void transactionComplete()
    {
        int val = nTransactions.incrementAndGet();
        if (val > rubis.upXactions + rubis.sessionXactions + rubis.downXactions) {
            setEndOfSimulation();
        }
    }

  /**
   * As the monitoring program now logs activity in a temporary
   * location, we need to scp the files over
   ** 
   * @param node node to launch monitoring program on
   * @param fileName full path and name of file that has the monitoring data
   * @param outputDir full path name of the local directory the log
   *                  file shoudl be copied into
   */
  private void getMonitoredData(String node, String fileName, 
				String outputDir)
  {
      String [] scpCmd = new String[3];
      String lastName = fileName.substring(fileName.lastIndexOf('/')+1);

      scpCmd[0] = rubis.getMonitoringScp();
      scpCmd[1] = node + ":"+fileName;
      // As the sar log is a binary file, rename it
      scpCmd[2] = outputDir+"/" + lastName + ".bin";
      File outputDirFile = new File(outputDir);
      outputDir = outputDirFile.getAbsolutePath();
      try
      {
        System.out.println(
                           "&nbsp &nbsp Command is: "
                           + scpCmd[0]
                           + " "
                           + scpCmd[1]
                           + " "
                           + scpCmd[2]
                           + "<br>\n");

        Process p = my_exec(scpCmd);
        p.waitFor();
        // Now, convert the binary file into ascii form
        int fullTimeInSec =
          (rubis.getUpRampTime()
           + rubis.getSessionTime()
           + rubis.getDownRampTime())
           / 1000
           + 5;
        String[] convCmd = new String[6];  
        convCmd[0] = rubis.getMonitoringRsh();
        convCmd[1] = "-x";
        convCmd[2] = "theremin";
        convCmd[3] = "/bin/bash";
        convCmd[4] = "-c";
        convCmd[5] = "'LANG=en_GB.UTF-8 " + rubis.getMonitoringProgram() + " "
            + rubis.getMonitoringOptions() + " "
            + rubis.getMonitoringSampling() + " "
            + fullTimeInSec
            + " -f " +  outputDir + "/" + lastName + ".bin"
            + " > " + outputDir + "/" + lastName + "'";        
        System.out.println("&nbsp &nbsp Command is: "+convCmd[0]+" "+convCmd[1]+" "+convCmd[2]+" "+convCmd[3]+" "+convCmd[4]+" "+convCmd[5]+"<br>\n");
        p = my_exec(convCmd);
        p.waitFor();
      }
      catch (InterruptedException ie) 
      {
          System.out.println(
			     "An error occured while executing "
			     + "monitoring program ("
			     + ie.getMessage()
			     + ")");

      }
      catch (IOException ioe)
      {
          System.out.println(
			     "An error occured while executing "
			     + "monitoring program ("
			     + ioe.getMessage()
			     + ")");
      }
  }

  /**
   * As the remote HTML files are created in a temporary
   * location, we need to scp these files over
   ** 
   * @param node node to launch monitoring program on
   * @param fileName full path and name of file that has the monitoring data
   * @param outputDir full path name of the local directory the log
   *                  file shoudl be copied into
   */
  private void getHTMLData(String node, String fileName, 
				String outputDir)
  {
      String [] scpCmd = new String[3];

      scpCmd[0] = rubis.getMonitoringScp();
      scpCmd[1] = node + ":"+fileName;
      scpCmd[2] = outputDir+"/";    

      try
      {
          System.out.println(
                             "&nbsp &nbsp Command is: "
                             + scpCmd[0]
                             + " "
                             + scpCmd[1]
                             + " "
                             + scpCmd[2]
                             + "<br>\n");

          Process p = my_exec(scpCmd);
          p.waitFor();

      }
      catch (InterruptedException ie) 
      {
          System.out.println("An error occured while executing "
			     + "monitoring program ("
			     + ie.getMessage()+ ")");

      }
      catch (IOException ioe)
      {
          System.out.println("An error occured while executing "
			     + "monitoring program ("
			     + ioe.getMessage() + ")");
      }
  }

  /**
   * Start the monitoring program specified in rubis.properties
   * on a remote node and redirect the output in a file local
   * to this node (we are more happy if it is on a NFS volume)
   *
   * @param node node to launch monitoring program on
   * @param outputFileName full path and name of file to redirect output into
   * @return the <code>Process</code> object created
   */
  private Process startMonitoringProgram(String node, String outputFileName)
  {
    int fullTimeInSec =
      (rubis.getUpRampTime()
        + rubis.getSessionTime()
        + rubis.getDownRampTime())
        / 1000
        + 5;
    // Give 5 seconds extra for init

    // First, try to wipe out the old log files as sar (sysstat)
    // appends for binary mode
    try
    {
      String[] delFiles = new String[4];
      Process delProcess;  
      delFiles[0] = rubis.getMonitoringRsh();
      delFiles[1] = "-x";
      delFiles[2] = node.trim();
      delFiles[3] = "rm -f "+outputFileName;
      System.out.println("&nbsp &nbsp Command is: "+delFiles[0]+" "+delFiles[1]+" "+delFiles[2]+" "+delFiles[3]+"<br>\n");
      delProcess = my_exec(delFiles);
      delProcess.waitFor();
    }
    catch (IOException ioe)
    {
      System.out.println(
        "An error occured while deleting old log files ("
          + ioe.getMessage() + ")");
      return null;
    }
    catch (InterruptedException ie)
    {
      System.out.println(
        "An error occured while deleting old log files ("
          + ie.getMessage() + ")");
      return null;
    }

    try
    {
      String[] cmd = new String[6];
      cmd[0] = rubis.getMonitoringRsh();
      cmd[1] = "-x";
      cmd[2] = node.trim();
      cmd[3] = "/bin/bash";
      cmd[4] = "-c";
      cmd[5] = "'LANG=en_GB.UTF-8 " + rubis.getMonitoringProgram() + " "
          + rubis.getMonitoringOptions() + " " 
          + rubis.getMonitoringSampling() + " "
          + fullTimeInSec
          + " -o " + outputFileName + "'";
      System.out.println(
        "&nbsp &nbsp Command is: " + cmd[0] + " " + cmd[1] + " " + cmd[2] +
          " " + cmd[3] + " " + cmd[4] + " " + cmd[5] + "<br>\n");
      return my_exec(cmd);
    }
    catch (IOException ioe)
    {
      System.out.println(
        "An error occured while executing monitoring program ("
          + ioe.getMessage()
          + ")");
      return null;
    }
  }

  /**
   * Run the node_info.sh script on the remote node and
   * just forward what we get from standard output.
   *
   * @param node node to get information from
   */
  private void printNodeInformation(String node)
  {
    try
    {
      File dir = new File(".");
      /*      String nodeInfoProgram = dir.getCanonicalPath() + "/bench-scripts/node_info.sh"; */
      String nodeInfoProgram = "/bin/echo \"Host  : \"`/bin/hostname` ; " +
          "/bin/echo \"Kernel: \"`/bin/cat /proc/version` ; " +
          "/bin/grep net /proc/pci ; " +
          "/bin/grep processor /proc/cpuinfo ; " +
          "/bin/grep vendor_id /proc/cpuinfo ; " +
          "/bin/grep model /proc/cpuinfo ; " +
          "/bin/grep MHz /proc/cpuinfo ; " +
          "/bin/grep cache /proc/cpuinfo ; " +
          "/bin/grep MemTotal /proc/meminfo ; " +
          "/bin/grep SwapTotal /proc/meminfo ";

      String[] cmd = new String[4];
      cmd[0] = rubis.getMonitoringRsh();
      cmd[1] = "-x";
      cmd[2] = node;
      cmd[3] = nodeInfoProgram;
      Process p = my_exec(cmd);
      BufferedReader read =
        new BufferedReader(new InputStreamReader(p.getInputStream()));
      String msg;
      while ((msg = read.readLine()) != null) {
	System.out.println(msg + "<br>");
      }
      read.close();
    }
    catch (Exception ioe)
    {
      System.out.println(
        "An error occured while getting node information ("
          + ioe.getMessage()
          + ")");
    }
  }

    // Austin
    private static void waitForTimeOrXactions(int ms, int xactions)
    {
        long start = System.currentTimeMillis();
        nTransactions.set(0);
        
        while (start + ms > System.currentTimeMillis() &&
               nTransactions.get() < xactions) {
            try
            {
              Thread.currentThread().sleep(100);
            }
            catch (java.lang.InterruptedException ie)
            {
              System.err.println("ClientEmulator has been interrupted.");
              return;
            }
        }
        if (nTransactions.get() >= xactions) {
            System.err.println("Transaction target of " + xactions +
                               " reached in " +
                               (System.currentTimeMillis() - start) + " ms.");
        } else {
            System.err.println("Time target of " + ms + " ms reached in " + nTransactions.get() + " transactions.");
        }
    }

  /**
   * Main program take an optional output file argument only 
   * if it is run on as a remote client.
   *
   * @param args optional output file if run as remote client
   */
  public static void main(String[] args)
  {
    GregorianCalendar startDate;
    GregorianCalendar endDate;
    GregorianCalendar upRampDate;
    GregorianCalendar runSessionDate;
    GregorianCalendar downRampDate;
    GregorianCalendar endDownRampDate;
    Process webServerMonitor = null;
    Process cjdbcServerMonitor = null;
    Process[]         dbServerMonitor = null;
    Process[]         ejbServerMonitor = null;
    Process[]         servletsServerMonitor = null;
    Process clientMonitor;
    Process[] remoteClientMonitor = null;
    Process[] remoteClient = null;
    String reportDir = "";
    String tmpDir = "/tmp/";
    boolean           isMainClient = (args.length <= 2); // Check if we are the main client
    String propertiesFileName;
    
    origStderr = System.err;

    if (isMainClient)
    {
      // Start by creating a report directory and redirecting output to an index.html file
      System.out.println(
        "RUBiS client emulator - (C) Rice University/INRIA 2001\n");

      if (args.length <= 1)
      {
        reportDir = "bench/"+TimeManagement.currentDateToString()+"/";
        reportDir = reportDir.replace(' ', '@');
      }
      else
      {
        reportDir = "bench/"+args[1];
      }
      try
      {
        System.out.println("Creating report directory " + reportDir);
        File dir = new File(reportDir);
        dir.mkdirs();
        if (!dir.isDirectory())
        {
          System.out.println(
            "Unable to create "
              + reportDir
              + " using current directory instead");
          reportDir = "./";
        }
        else
          reportDir = dir.getCanonicalPath() + "/";
        System.out.println(
          "Redirecting output to '" + reportDir + "index.html'");
        PrintStream outputStream =
          new PrintStream(new FileOutputStream(reportDir + "index.html"));
        System.out.println("Please wait while experiment is running ...");
        System.setOut(outputStream);
        //System.setErr(outputStream);
      }
      catch (Exception e)
      {
        System.out.println(
          "Output redirection failed, displaying results on standard output ("
            + e.getMessage()
            + ")");
      }
      System.out.println(
        "<h2>RUBiS client emulator - (C) Rice University/INRIA 2001</h2><p>\n");
      startDate = new GregorianCalendar();
      System.out.println(
        "<h3>Test date: "
          + TimeManagement.dateToString(startDate)
          + "</h3><br>\n");

      System.out.println("<A HREF=\"#config\">Test configuration</A><br>");
      System.out.println("<A HREF=\"trace_client0.html\">Test trace</A><br>");
      System.out.println(
        "<A HREF=\"perf.html\">Test performance report</A><br><p>");
      System.out.println("<p><hr><p>");

      System.out.println(
        "<CENTER><A NAME=\"config\"></A><h2>*** Test configuration ***</h2></CENTER>");
      if (args.length == 0)
        propertiesFileName = "rubis";
      else
        propertiesFileName = args[0];
    }
    else
    {
      System.out.println(
        "RUBiS remote client emulator - (C) Rice University/INRIA 2001\n");
      startDate = new GregorianCalendar();
      propertiesFileName = args[2];
    }

    ClientEmulator client = new ClientEmulator(propertiesFileName);
    // Get also rubis.properties info

    Stats stats = new Stats(client.rubis.getNbOfRows());
    Stats upRampStats = new Stats(client.rubis.getNbOfRows());
    Stats runSessionStats = new Stats(client.rubis.getNbOfRows());
    Stats downRampStats = new Stats(client.rubis.getNbOfRows());
    Stats allStats = new Stats(client.rubis.getNbOfRows());
    UserSession[] sessions = new UserSession[client.rubis.getNbOfClients()];
    boolean cjdbcFlag = client.rubis.getCJDBCServerName() != null
        && !client.rubis.getCJDBCServerName().equals("");
    System.out.println("<p><hr><p>");

    if (isMainClient)
    {
      // Start remote clients
      System.out.println(
        "Total number of clients for this experiment: "
          + (client.rubis.getNbOfClients()
            * (client.rubis.getRemoteClients().size() + 1))
          + "<br>");
      remoteClient = new Process[client.rubis.getRemoteClients().size()];
      for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
      {
        try
        {
          System.out.println(
            "ClientEmulator: Starting remote client on "
              + client.rubis.getRemoteClients().get(i)
              + "<br>\n");
          String[] rcmdClient = new String[3];
          rcmdClient[0] = client.rubis.getMonitoringRsh();
          rcmdClient[1] = (String) client.rubis.getRemoteClients().get(i);
          rcmdClient[2] =
            client.rubis.getClientsRemoteCommand() + " "
              + tmpDir + "trace_client" + (i + 1) + ".html "
              + tmpDir + "stat_client" + (i + 1) + ".html" + " "
              + propertiesFileName;
          remoteClient[i] = my_exec(rcmdClient);
          System.out.println(
            "&nbsp &nbsp Command is: " + rcmdClient[0] + " " + rcmdClient[1]
              + " "+ rcmdClient[2] + "<br>\n");
        }
        catch (IOException ioe)
        {
          System.out.println(
            "An error occured while executing remote client ("
              + ioe.getMessage() + ")");
        }
      }

      // Start monitoring programs
      System.out.println(
        "<CENTER></A><A NAME=\"trace\"><h2>*** Monitoring ***</h2></CENTER>");

      // Monitor Web server
      {
        int i = 0;
        for (URL url : client.rubis.getWebServers()) {
          String host = url.getHost();
          System.out.println(
            "ClientEmulator: Starting monitoring program on Web server "
              + host + "<br>\n");
          webServerMonitor =
            client.startMonitoringProgram(host,
                                          tmpDir + "web_server" + (i++));
        }
      }

      // Monitor C-JDBC server (if any)
      if (cjdbcFlag) 
      {
        System.out.println(
          "ClientEmulator: Starting monitoring program on CJDBC server "
            + client.rubis.getCJDBCServerName()
            + "<br>\n");
        cjdbcServerMonitor =
          client.startMonitoringProgram(
            client.rubis.getCJDBCServerName(),
            tmpDir + "cjdbc_server");
      }

      if (client.rubis.getDBServerNames().size() > 0)
        dbServerMonitor = new Process[client.rubis.getDBServerNames().size()];       
      // Monitor Database server
      for (int i = 0; i < client.rubis.getDBServerNames().size(); i++)
      {
        System.out.println("ClientEmulator: Starting monitoring program on Database server "+client.rubis.getDBServerNames().get(i)+"<br>\n");
        dbServerMonitor[i] = client.startMonitoringProgram((String)client.rubis.getDBServerNames().get(i), tmpDir+"db_server"+i);
      }
        
      if (client.rubis.getServletsServerNames().size() > 0)
      servletsServerMonitor = new Process[client.rubis.getServletsServerNames().size()];
      // Monitoring Servlets server, if any
      for (int i = 0; i < client.rubis.getServletsServerNames().size(); i++)
      {
        System.out.println("ClientEmulator: Starting monitoring program on Servlets server "+client.rubis.getServletsServerNames().get(i)+"<br>\n");
        servletsServerMonitor[i] = client.startMonitoringProgram((String)client.rubis.getServletsServerNames().get(i), tmpDir+"servlets_server"+i);
      }
     
      if (client.rubis.getEJBServerNames().size() > 0)
      ejbServerMonitor = new Process[client.rubis.getEJBServerNames().size()];
      // Monitoring EJB server, if any
      for (int i = 0; i < client.rubis.getEJBServerNames().size(); i++)
      {
        System.out.println("ClientEmulator: Starting monitoring program on EJB server "+client.rubis.getEJBServerNames().get(i)+"<br>\n");
        ejbServerMonitor[i] = client.startMonitoringProgram((String)client.rubis.getEJBServerNames().get(i), tmpDir+"ejb_server"+i);
      }

      // Monitor local client
      System.out.println(
        "ClientEmulator: Starting monitoring program locally on client<br>\n");
      clientMonitor =
        client.startMonitoringProgram("localhost", tmpDir + "client0");

      remoteClientMonitor = new Process[client.rubis.getRemoteClients().size()];
      // Monitor remote clients
      for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
      {
        System.out.println(
          "ClientEmulator: Starting monitoring program locally on client<br>\n");
        remoteClientMonitor[i] =
          client.startMonitoringProgram(
            (String) client.rubis.getRemoteClients().get(i),
            tmpDir + "client" + (i + 1));
      }

      // Redirect output for traces
      try
      {
        PrintStream outputStream =
          new PrintStream(
            new FileOutputStream(reportDir + "trace_client0.html"));
        System.setOut(outputStream);
        //System.setErr(outputStream);
      }
      catch (FileNotFoundException fnf)
      {
        System.err.println(
          "Unable to redirect main client output, got error ("
            + fnf.getMessage() + ")<br>");
      }
    }
    else
    { // Redirect output of remote clients
      System.out.println("Redirecting output to '" + args[0] + "'");
      try
      {
        PrintStream outputStream =
          new PrintStream(new FileOutputStream(args[0]));
        System.out.println("Please wait while experiment is running ...");
        System.setOut(outputStream);
        //System.setErr(outputStream);
      }
      catch (Exception e)
      {
        System.out.println(
          "Output redirection failed, displaying results on standard output ("
            + e.getMessage()
            + ")");
      }
      startDate = new GregorianCalendar();
    }

    // #############################
    // ### TEST TRACE BEGIN HERE ###
    // #############################

    System.out.println(
      "<CENTER></A><A NAME=\"trace\"><h2>*** Test trace ***</h2></CENTER><p>");
    System.out.println(
      "<A HREF=\"trace_client0.html\">Main client traces</A><br>");
    for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
      System.out.println(
        "<A HREF=\"trace_client"
          + (i + 1)
          + ".html\">client1 ("
          + client.rubis.getRemoteClients().get(i)
          + ") traces</A><br>");
    System.out.println("<br><p>");
    System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#up\">Up ramp trace</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#run\">Runtime session trace</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#down\">Down ramp trace</A><br><p><p>");

    // Set the slowdown factor to its upramp value *before starting
    // threads* because this benchmark is really fucking stupid and it
    // assumes zero otherwise. Then it will proceed to have thousands
    // of threads continuously pound the web servers for absolutely no
    // reason.
    client.setSlowDownFactor(client.rubis.getUpRampSlowdown());
    
    // Run user sessions
    System.out.println("ClientEmulator: Starting " +
                       client.rubis.getNbOfClients() + " session threads<br>");
    for (int i = 0; i < client.rubis.getNbOfClients(); i++)
    {
      sessions[i] =
        new UserSession("UserSession" + i, client.urlGen, client.rubis, stats);
      sessions[i].start();
      // Delay 5 milliseconds to lessen the load on the system.
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        // Ignore
      }
    }

    // Start up-ramp
    System.out.println("<br><A NAME=\"up\"></A>");
    System.out.println(
      "<h3>ClientEmulator: Switching to ** UP RAMP **</h3><br><p>");
    upRampDate = new GregorianCalendar();
    waitForTimeOrXactions(client.rubis.getUpRampTime(),
                          client.rubis.upXactions);
    upRampStats.merge(stats);
    stats.reset();
    // Note that as this is not atomic we may lose some stats here ...

    // Start runtime session
    System.out.println("<br><A NAME=\"run\"></A>");
    System.out.println(
      "<h3>ClientEmulator: Switching to ** RUNTIME SESSION **</h3><br><p>");
    client.setSlowDownFactor(1);
    runSessionDate = new GregorianCalendar();
    waitForTimeOrXactions(client.rubis.getSessionTime(),
                          client.rubis.sessionXactions);
    runSessionStats.merge(stats);
    stats.reset();
    // Note that as this is not atomic we may lose some stats here ...

    // Start down-ramp
    System.out.println("<br><A NAME=\"down\"></A>");
    System.out.println(
      "<h3>ClientEmulator: Switching to ** DOWN RAMP **</h3><br><p>");
    client.setSlowDownFactor(client.rubis.getDownRampSlowdown());
    downRampDate = new GregorianCalendar();
    waitForTimeOrXactions(client.rubis.getDownRampTime(),
                          client.rubis.downXactions);
    downRampStats.merge(stats);
    endDownRampDate = new GregorianCalendar();

    // Wait for completion
    client.setEndOfSimulation();
    System.out.println("ClientEmulator: Shutting down threads ...<br>");
    for (int i = 0; i < client.rubis.getNbOfClients(); i++)
    {
      try
      {
        sessions[i].join(2000);
      }
      catch (java.lang.InterruptedException ie)
      {
        System.err.println(
          "ClientEmulator: Thread " + i + " has been interrupted.");
      }
    }
    System.out.println("Done\n");
    endDate = new GregorianCalendar();
    allStats.merge(stats);
    allStats.merge(runSessionStats);
    allStats.merge(upRampStats);
    System.out.println("<p><hr><p>");

    // #############################################
    // ### EXPERIMENT IS OVER, COLLECT THE STATS ###
    // #############################################

    // All clients completed, here is the performance report !
    // but first redirect the output
    try
    {
      PrintStream outputStream;
      if (isMainClient)
        outputStream =
          new PrintStream(new FileOutputStream(reportDir + "perf.html"));
      else
        outputStream = new PrintStream(new FileOutputStream(args[1]));
      System.setOut(outputStream);
      //System.setErr(outputStream);
    }
    catch (Exception e)
    {
      System.out.println(
        "Output redirection failed, displaying results on standard output ("
          + e.getMessage()
          + ")");
    }

    System.out.println(
      "<center><h2>*** Performance Report ***</h2></center><br>");
    System.out.println(
      "<A HREF=\"perf.html\">Overall performance report</A><br>");
    System.out.println(
      "<A HREF=\"stat_client0.html\">Main client (localhost) statistics</A><br>");
    for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
      System.out.println(
        "<A HREF=\"stat_client"
          + (i + 1)
          + ".html\">client1 ("
          + client.rubis.getRemoteClients().get(i)
          + ") statistics</A><br>");
    System.out.println("<A HREF=\"db_graphs.html\">Database graphs</A><br>");
    if (client.rubis.getServletsServerNames().size() > 0)
      System.out.println("<A HREF=\"servlets_graphs.html\">Servlets graphs</A><br>");
    if (client.rubis.getEJBServerNames().size() > 0)
      System.out.println("<A HREF=\"ejb_graphs.html\">EJB graphs</A><br>");
    
    System.out.println(
      "<p><br>&nbsp&nbsp&nbsp<A HREF=\"perf.html#node\">Node information</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#time\">Test timing information</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#up_stat\">Up ramp statistics</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#run_stat\">Runtime session statistics</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#down_stat\">Down ramp statistics</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#all_stat\">Overall statistics</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#cpu_graph\">CPU usage graphs</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#procs_graph\">Processes usage graphs</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#mem_graph\">Memory usage graph</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#disk_graph\">Disk usage graphs</A><br>");
    System.out.println(
      "&nbsp&nbsp&nbsp<A HREF=\"#net_graph\">Network usage graphs</A><br>");

    if (isMainClient)
    {
      // Get information about each node
      System.out.println(
        "<br><A NAME=\"node\"></A><h3>Node Information</h3><br>");

      // Web server
      System.out.println("<B>Web server</B><br>");
      for (URL url : client.rubis.getWebServers())
        client.printNodeInformation(url.getHost());

      // C-JDBC server
      if (cjdbcFlag)
      {
        System.out.println("<br><B>C-JDBC server</B><br>");
        client.printNodeInformation((String)client.rubis.getCJDBCServerName());
      }

      // Database server
      System.out.println("<br><B>Database server</B><br>");
      client.printNodeInformation((String)client.rubis.getDBServerNames().get(0));
      
      // Servlets server, if any
      if (client.rubis.getServletsServerNames().size() > 0)
      {
         System.out.println("<br><B>Servlets server</B><br>");
          client.printNodeInformation((String)client.rubis.getServletsServerNames().get(0));
      }
      
      //    EJB server, if any
       if (client.rubis.getEJBServerNames().size() > 0)
       {
          System.out.println("<br><B>EJB server</B><br>");
          client.printNodeInformation((String)client.rubis.getEJBServerNames().get(0));
       }
      
      // Client
      System.out.println("<br><B>Local client</B><br>");
      client.printNodeInformation("localhost");

      // Remote Clients
      for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
      {
        System.out.println("<br><B>Remote client " + i + "</B><br>");
        client.printNodeInformation(
          (String) client.rubis.getRemoteClients().get(i));
      }

      try
      {
        PrintStream outputStream = new PrintStream(new FileOutputStream(reportDir+"db_graphs.html"));
        System.setOut(outputStream);
        //System.setErr(outputStream);
      }
      catch (Exception ioe)
      {
        System.out.println("An error occured while creating file ("+ioe.getMessage()+")");
      }

      System.out.println("<center><h2>*** Database servers graphs ***</h2></center><br>");
      System.out.println("<A HREF=\"perf.html\">Overall performance report</A><br>");
      System.out.println("<A HREF=\"stat_client0.html\">Main client (localhost) statistics</A><br>");
      for (int i = 0 ; i < client.rubis.getRemoteClients().size() ; i++)
        System.out.println("<A HREF=\"stat_client"+(i+1)+".html\">client1 ("+client.rubis.getRemoteClients().get(i)+") statistics</A><br>");
      System.out.println("<A HREF=\"db_graphs.html\">Database graphs</A><br>");
      if (client.rubis.getServletsServerNames().size() > 0)
        System.out.println("<A HREF=\"servlets_graphs.html\">Servlets graphs</A><br>");
      if (client.rubis.getEJBServerNames().size() > 0)
        System.out.println("<A HREF=\"ejb_graphs.html\">EJB graphs</A><br>");

      System.out.println("<p><br>&nbsp&nbsp&nbsp<A HREF=\"#node\">Node information</A><br>");
      System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#cpu_graph\">CPU usage graphs</A><br>");
      System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#procs_graph\">Processes usage graphs</A><br>");
      System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#mem_graph\">Memory usage graph</A><br>");
      System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#disk_graph\">Disk usage graphs</A><br>");
      System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#net_graph\">Network usage graphs</A><br>");

      System.out.println("<br><A NAME=\"node\"></A><h3>Node Information</h3><br>");
      for (int i = 0; i < client.rubis.getDBServerNames().size(); i++)
      {
        System.out.println("<br><B>Database server "+i+"</B><br>");
        client.printNodeInformation((String)client.rubis.getDBServerNames().get(i));
      }

      System.out.println("<br><A NAME=\"cpu_graph\"></A>");
      System.out.println("<br><h3>CPU Usage graphs</h3><p>");
      System.out.println("<IMG SRC=\"db_cpu_busy."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_cpu_idle."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_cpu_user_kernel."+client.rubis.getGnuPlotTerminal()+"\">");

      System.out.println("<br><A NAME=\"procs_graph\"></A>");
      System.out.println("<br><h3>Processes Usage graphs</h3><p>");
      System.out.println("<IMG SRC=\"db_procs."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_ctxtsw."+client.rubis.getGnuPlotTerminal()+"\">");

      System.out.println("<br><A NAME=\"mem_graph\"></A>");
      System.out.println("<br><h3>Memory Usage graphs</h3><p>");
      System.out.println("<IMG SRC=\"db_mem_usage."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_mem_cache."+client.rubis.getGnuPlotTerminal()+"\">");

      System.out.println("<br><A NAME=\"disk_graph\"></A>");
      System.out.println("<br><h3>Disk Usage graphs</h3><p>");
      System.out.println("<IMG SRC=\"db_disk_rw_req."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_disk_tps."+client.rubis.getGnuPlotTerminal()+"\">");

      System.out.println("<br><A NAME=\"net_graph\"></A>");
      System.out.println("<br><h3>Network Usage graphs</h3><p>");
      System.out.println("<IMG SRC=\"db_net_rt_byt."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_net_rt_pack."+client.rubis.getGnuPlotTerminal()+"\">");
      System.out.println("<IMG SRC=\"db_socks."+client.rubis.getGnuPlotTerminal()+"\">");

      if (client.rubis.getServletsServerNames().size() > 0)
      {
        try
        {
          PrintStream outputStream = new PrintStream(new FileOutputStream(reportDir+"servlets_graphs.html"));
          System.setOut(outputStream);
          //System.setErr(outputStream);
        }
        catch (Exception ioe)
        {
          System.out.println("An error occured while creating file ("+ioe.getMessage()+")");
        }

        System.out.println("<center><h2>*** Servlets servers graphs ***</h2></center><br>");
        System.out.println("<A HREF=\"perf.html\">Overall performance report</A><br>");
        System.out.println("<A HREF=\"stat_client0.html\">Main client (localhost) statistics</A><br>");
        for (int i = 0 ; i < client.rubis.getRemoteClients().size() ; i++)
          System.out.println("<A HREF=\"stat_client"+(i+1)+".html\">client1 ("+client.rubis.getRemoteClients().get(i)+") statistics</A><br>");
        System.out.println("<A HREF=\"db_graphs.html\">Database graphs</A><br>");
        if (client.rubis.getServletsServerNames().size() > 0)
          System.out.println("<A HREF=\"servlets_graphs.html\">Servlets graphs</A><br>");
        if (client.rubis.getEJBServerNames().size() > 0)
          System.out.println("<A HREF=\"ejb_graphs.html\">EJB graphs</A><br>");

        System.out.println("<p><br>&nbsp&nbsp&nbsp<A HREF=\"#node\">Node information</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#cpu_graph\">CPU usage graphs</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#procs_graph\">Processes usage graphs</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#mem_graph\">Memory usage graph</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#disk_graph\">Disk usage graphs</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#net_graph\">Network usage graphs</A><br>");

        System.out.println("<br><A NAME=\"node\"></A><h3>Node Information</h3><br>");
        for (int i = 0; i < client.rubis.getServletsServerNames().size(); i++)
        {
          System.out.println("<br><B>Servlets server "+i+"</B><br>");
          client.printNodeInformation((String)client.rubis.getServletsServerNames().get(i));
        }

        System.out.println("<br><A NAME=\"cpu_graph\"></A>");
        System.out.println("<br><h3>CPU Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"servlets_cpu_busy."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_cpu_idle."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_cpu_user_kernel."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"procs_graph\"></A>");
        System.out.println("<br><h3>Processes Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"servlets_procs."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_ctxtsw."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"mem_graph\"></A>");
        System.out.println("<br><h3>Memory Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"servlets_mem_usage."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_mem_cache."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"disk_graph\"></A>");
        System.out.println("<br><h3>Disk Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"servlets_disk_rw_req."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_disk_tps."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"net_graph\"></A>");
        System.out.println("<br><h3>Network Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"servlets_net_rt_byt."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_net_rt_pack."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"servlets_socks."+client.rubis.getGnuPlotTerminal()+"\">");
      }

      if (client.rubis.getEJBServerNames().size() > 0)
      {
        try
        {
          PrintStream outputStream = new PrintStream(new FileOutputStream(reportDir+"ejb_graphs.html"));
          System.setOut(outputStream);
          //System.setErr(outputStream);
        }
        catch (Exception ioe)
        {
          System.out.println("An error occured while creating file ("+ioe.getMessage()+")");
        }

        System.out.println("<center><h2>*** EJB servers graphs ***</h2></center><br>");
        System.out.println("<A HREF=\"perf.html\">Overall performance report</A><br>");
        System.out.println("<A HREF=\"stat_client0.html\">Main client (localhost) statistics</A><br>");
        for (int i = 0 ; i < client.rubis.getRemoteClients().size() ; i++)
          System.out.println("<A HREF=\"stat_client"+(i+1)+".html\">client1 ("+client.rubis.getRemoteClients().get(i)+") statistics</A><br>");
        System.out.println("<A HREF=\"db_graphs.html\">Database graphs</A><br>");
        if (client.rubis.getServletsServerNames().size() > 0)
          System.out.println("<A HREF=\"servlets_graphs.html\">Servlets graphs</A><br>");
        if (client.rubis.getEJBServerNames().size() > 0)
          System.out.println("<A HREF=\"ejb_graphs.html\">EJB graphs</A><br>");

        System.out.println("<p><br>&nbsp&nbsp&nbsp<A HREF=\"#node\">Node information</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#cpu_graph\">CPU usage graphs</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#procs_graph\">Processes usage graphs</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#mem_graph\">Memory usage graph</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#disk_graph\">Disk usage graphs</A><br>");
        System.out.println("&nbsp&nbsp&nbsp<A HREF=\"#net_graph\">Network usage graphs</A><br>");

        System.out.println("<br><A NAME=\"node\"></A><h3>Node Information</h3><br>");
        for (int i = 0; i < client.rubis.getEJBServerNames().size(); i++)
        {
          System.out.println("<br><B>EJB server "+i+"</B><br>");
          client.printNodeInformation((String)client.rubis.getEJBServerNames().get(i));
        }

       System.out.println("<br><A NAME=\"cpu_graph\"></A>");
        System.out.println("<br><h3>CPU Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"ejb_cpu_busy."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_cpu_idle."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_cpu_user_kernel."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"procs_graph\"></A>");
        System.out.println("<br><h3>Processes Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"ejb_procs."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_ctxtsw."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"mem_graph\"></A>");
        System.out.println("<br><h3>Memory Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"ejb_mem_usage."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_mem_cache."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"disk_graph\"></A>");
        System.out.println("<br><h3>Disk Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"ejb_disk_rw_req."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_disk_tps."+client.rubis.getGnuPlotTerminal()+"\">");

        System.out.println("<br><A NAME=\"net_graph\"></A>");
        System.out.println("<br><h3>Network Usage graphs</h3><p>");
        System.out.println("<IMG SRC=\"ejb_net_rt_byt."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_net_rt_pack."+client.rubis.getGnuPlotTerminal()+"\">");
        System.out.println("<IMG SRC=\"ejb_socks."+client.rubis.getGnuPlotTerminal()+"\">");
      }

      try
      {
        PrintStream outputStream =
          new PrintStream(
            new FileOutputStream(reportDir + "stat_client0.html"));
        System.setOut(outputStream);
        //System.setErr(outputStream);
        System.out.println(
          "<center><h2>*** Performance Report ***</h2></center><br>");
        System.out.println(
          "<A HREF=\"perf.html\">Overall performance report</A><br>");
        System.out.println(
          "<A HREF=\"stat_client0.html\">Main client (localhost) statistics</A><br>");
        for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
          System.out.println(
            "<A HREF=\"stat_client"
              + (i + 1)
              + ".html\">client1 ("
              + client.rubis.getRemoteClients().get(i)
              + ") statistics</A><br>");
              
        System.out.println("<A HREF=\"db_graphs.html\">Database graphs</A><br>");
          if (client.rubis.getServletsServerNames().size() > 0)
        System.out.println("<A HREF=\"servlets_graphs.html\">Servlets graphs</A><br>");
        if (client.rubis.getEJBServerNames().size() > 0)
          System.out.println("<A HREF=\"ejb_graphs.html\">EJB graphs</A><br>");
          
        System.out.println(
          "<p><br>&nbsp&nbsp&nbsp<A HREF=\"perf.html#node\">Node information</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#time\">Test timing information</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#up_stat\">Up ramp statistics</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#run_stat\">Runtime session statistics</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#down_stat\">Down ramp statistics</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#all_stat\">Overall statistics</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#cpu_graph\">CPU usage graphs</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#procs_graph\">Processes usage graphs</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#mem_graph\">Memory usage graph</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#disk_graph\">Disk usage graphs</A><br>");
        System.out.println(
          "&nbsp&nbsp&nbsp<A HREF=\"#net_graph\">Network usage graphs</A><br>");
      }
      catch (Exception ioe)
      {
        System.out.println(
          "An error occured while getting node information ("
            + ioe.getMessage()
            + ")");
      }
    }

    // Test timing information
    System.out.println(
      "<br><p><A NAME=\"time\"></A><h3>Test timing information</h3><p>");
    System.out.println("<TABLE BORDER=1>");
    System.out.println(
      "<TR><TD><B>Test start</B><TD>" + TimeManagement.dateToString(startDate));
    System.out.println(
      "<TR><TD><B>Up ramp start</B><TD>"
        + TimeManagement.dateToString(upRampDate));
    System.out.println(
      "<TR><TD><B>Runtime session start</B><TD>"
        + TimeManagement.dateToString(runSessionDate));
    System.out.println(
      "<TR><TD><B>Down ramp start</B><TD>"
        + TimeManagement.dateToString(downRampDate));
    System.out.println(
      "<TR><TD><B>Test end</B><TD>" + TimeManagement.dateToString(endDate));
    System.out.println(
      "<TR><TD><B>Up ramp length</B><TD>"
        + TimeManagement.diffTime(upRampDate, runSessionDate)
        + " (requested "
        + client.rubis.getUpRampTime()
        + " ms)");
    System.out.println(
      "<TR><TD><B>Runtime session length</B><TD>"
        + TimeManagement.diffTime(runSessionDate, downRampDate)
        + " (requested "
        + client.rubis.getSessionTime()
        + " ms)");
    System.out.println(
      "<TR><TD><B>Down ramp length</B><TD>"
        + TimeManagement.diffTime(downRampDate, endDownRampDate)
        + " (requested "
        + client.rubis.getDownRampTime()
        + " ms)");
    System.out.println(
      "<TR><TD><B>Total test length</B><TD>"
        + TimeManagement.diffTime(startDate, endDate));
    System.out.println("</TABLE><p>");

    // Stats for each ramp
    System.out.println("<br><A NAME=\"up_stat\"></A>");
    upRampStats.display_stats(
      "Up ramp",
      TimeManagement.diffTimeInMs(upRampDate, runSessionDate),
      false);
    System.out.println("<br><A NAME=\"run_stat\"></A>");
    runSessionStats.display_stats(
      "Runtime session",
      TimeManagement.diffTimeInMs(runSessionDate, downRampDate),
      false);
    System.out.println("<br><A NAME=\"down_stat\"></A>");
    downRampStats.display_stats(
      "Down ramp",
      TimeManagement.diffTimeInMs(downRampDate, endDownRampDate),
      false);
    System.out.println("<br><A NAME=\"all_stat\"></A>");
    allStats.display_stats(
      "Overall",
      TimeManagement.diffTimeInMs(upRampDate, endDownRampDate),
      false);

    if (isMainClient)
    {
      
      try
      {
        Thread.sleep(10000);
        // XXX(Austin) This is a total hack.  The waits on the client
        // monitors block forever because nothing ever consumes their
        // stdout.  Add a redirect to /dev/null in
        // startMonitoringProgram.
        if (true)
          throw new Exception("Skipping waits");
        //      Wait for end of all monitors and remote clients
        for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
        {
         // The waitFor method only does not work: it hangs forever
          // XXX(Austin) exitValue throws an exception if the process
          // hasn't exited.  The old code is nonsense.
          //if (remoteClientMonitor[i].exitValue() != 0)
            {
                remoteClientMonitor[i].waitFor();
            }
            //if (remoteClient[i].exitValue() != 0)
            {
               remoteClient[i].waitFor();
            }
          //remoteClientMonitor[i].waitFor();
          //remoteClient[i].waitFor();
        }
        //if (webServerMonitor.exitValue() != 0)
        {
          webServerMonitor.waitFor();
        }
        //if (cjdbcServerMonitor.exitValue() != 0)
        if (cjdbcServerMonitor != null)
        {
          cjdbcServerMonitor.waitFor();
        }
        for (int i = 0; i < dbServerMonitor.length; i++)
          dbServerMonitor[i].waitFor();
        if (servletsServerMonitor != null)
        {
          for (int i = 0; i < servletsServerMonitor.length; i++)
                  servletsServerMonitor[i].waitFor();
        }          
        if (ejbServerMonitor != null)
        {
          for (int i = 0; i < ejbServerMonitor.length; i++)
                  ejbServerMonitor[i].waitFor();
        }      
      }
      catch (Exception e)
      {
        System.out.println(
          "An error occured while waiting for remote processes termination ("
            + e.getMessage()
            + ")");
        e.printStackTrace();
      }

      // Time to transfer (scp all the files over)
      try 
      {
        for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
        {
          client.getMonitoredData(
				        (String) client.rubis.getRemoteClients().get(i),
				        tmpDir + "client" + (i + 1),
				        reportDir);
        }

        for (int i = 0; i < dbServerMonitor.length; i++)
        {
	         client.getMonitoredData((String)client.rubis.getDBServerNames().get(i), 
                                  tmpDir+"db_server"+i, 
                                  reportDir);
        }

        // Web server
        {
          int i = 0;
          for (URL url : client.rubis.getWebServers())
            client.getMonitoredData(url.getHost(),
                                    tmpDir + "web_server" + (i++),
                                    reportDir);
        }

        // Local client
        client.getMonitoredData("localhost",
                                tmpDir + "client0",
                                reportDir);

        // C-JDBC server
        if (cjdbcFlag)
        {
          client.getMonitoredData(client.rubis.getCJDBCServerName(),
                                  tmpDir + "cjdbc_server",
                                  reportDir);
        }
      
        if (servletsServerMonitor != null)
        {
          for (int i = 0; i < servletsServerMonitor.length; i++)
              client.getMonitoredData((String)client.rubis.getServletsServerNames().get(i), 
                                      tmpDir+"servlets_server"+i,
                                      reportDir);
        }
        if (ejbServerMonitor != null)
        {
          for (int i = 0; i < ejbServerMonitor.length; i++)
            client.getMonitoredData((String)client.rubis.getEJBServerNames().get(i), 
                                    tmpDir+"ejb_server"+i,
                                    reportDir);
        }
        // Now transfer the remote client html files
        for (int i = 0; i < client.rubis.getRemoteClients().size(); i++)
        {
          client.getHTMLData((String) client.rubis.getRemoteClients().get(i),
                                  tmpDir + "trace_client" + (i + 1) + ".html ",
                                  reportDir);
          client.getHTMLData((String) client.rubis.getRemoteClients().get(i),
                                  tmpDir + "stat_client" + (i + 1) + ".html ",
                                  reportDir);
        }
      }
      catch (Exception e)
      {
          System.out.println(
                             "An error occured while transferring log files ("
                             + e.getMessage()
                             + ")");
      }

      // Generate the graphics 
      try
      {
        String[] cmd = null;
        if (client.rubis.getEJBServerNames().size() > 0)
        {
          cmd = new String[7];
          cmd[0] = "bench-scripts/ejb_generate_graphs.sh";
        }
        else if (client.rubis.getServletsServerNames().size() > 0)
        {
          cmd = new String[6];
          cmd[0] = "bench-scripts/servlets_generate_graphs.sh";
        }
        else
        {
          cmd = new String[6];
          cmd[0] = "bench-scripts/generate_graphs.sh";
          cmd[5] = Integer.toString(client.rubis.getWebServers().size());
        }
        cmd[1] = reportDir;
        cmd[2] = client.rubis.getGnuPlotTerminal();
        cmd[3] = Integer.toString(client.rubis.getRemoteClients().size() + 1);
        cmd[4] = Integer.toString(client.rubis.getDBServerNames().size());
        if (client.rubis.getServletsServerNames().size() > 0)
          cmd[5] = Integer.toString(client.rubis.getServletsServerNames().size());
        if (client.rubis.getEJBServerNames().size() > 0)
          cmd[6] = Integer.toString(client.rubis.getEJBServerNames().size());
        Process graph = my_exec(cmd);
        // Need to read input so program does not stall.
        BufferedReader read = new BufferedReader(new InputStreamReader(graph.getInputStream()));
        String msg;
        while ((msg = read.readLine()) != null) {
	  //   System.out.println(msg+"<br>");
	}
        read.close();
        graph.waitFor();
      }
      catch (Exception e)
      {
        System.out.println(
          "An error occured while generating the graphs ("
            + e.getMessage()
            + ")");
        e.printStackTrace();
      }
    }

    System.out.println("<br><A NAME=\"cpu_graph\"></A>");
    System.out.println("<br><h3>CPU Usage graphs</h3><p>");
    System.out.println("<TABLE>");
    System.out.println(
      "<TR><TD><IMG SRC=\"cpu_busy."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_cpu_busy."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_cpu_busy."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"cpu_idle."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_cpu_idle."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_cpu_idle."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"cpu_user_kernel."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_cpu_user_kernel."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_cpu_user_kernel."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println("</TABLE><p>");

    System.out.println("<br><A NAME=\"procs_graph\"></A>");
    System.out.println("<TABLE>");
    System.out.println("<br><h3>Processes Usage graphs</h3><p>");
    System.out.println(
      "<TR><TD><IMG SRC=\"procs."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_procs."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_procs."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"ctxtsw."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_ctxtsw."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_ctxtsw."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println("</TABLE><p>");

    System.out.println("<br><A NAME=\"mem_graph\"></A>");
    System.out.println("<br><h3>Memory Usage graph</h3><p>");
    System.out.println("<TABLE>");
    System.out.println(
      "<TR><TD><IMG SRC=\"mem_usage."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_mem_usage."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_mem_usage."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"mem_cache."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_mem_cache."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_mem_cache."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println("</TABLE><p>");

    System.out.println("<br><A NAME=\"disk_graph\"></A>");
    System.out.println("<br><h3>Disk Usage graphs</h3><p>");
    System.out.println("<TABLE>");
    System.out.println(
      "<TR><TD><IMG SRC=\"disk_rw_req."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_disk_rw_req."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_disk_rw_req."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"disk_tps."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_disk_tps."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_disk_tps."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println("</TABLE><p>");

    System.out.println("<br><A NAME=\"net_graph\"></A>");
    System.out.println("<br><h3>Network Usage graphs</h3><p>");
    System.out.println("<TABLE>");
    System.out.println(
      "<TR><TD><IMG SRC=\"net_rt_byt."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_net_rt_byt."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_net_rt_byt."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"net_rt_pack."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_net_rt_pack."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_net_rt_pack."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println(
      "<TR><TD><IMG SRC=\"socks."
        + client.rubis.getGnuPlotTerminal()
        + "\"><TD><IMG SRC=\"client_socks."
        + client.rubis.getGnuPlotTerminal()
        + "\">");
    if (cjdbcFlag)
    {
      System.out.println(
        "<TR><TD><IMG SRC=\"cjdbc_server_socks."
          + client.rubis.getGnuPlotTerminal()
          + "\">");
    }
    System.out.println("</TABLE><p>");

    if (isMainClient)
    {
      // Compute the global stats
      try
      {
        String[] cmd = new String[6];
        cmd[0] = "bench-scripts/compute_global_stats.awk";
        cmd[1] = "-v";
        cmd[2] = "path=" + reportDir;
        cmd[3] = "-v";
        cmd[4] =
          "nbscript="
            + Integer.toString(client.rubis.getRemoteClients().size() + 1);
        cmd[5] = reportDir + "stat_client0.html";
        Process computeStats = my_exec(cmd);
        computeStats.waitFor();
      }
      catch (Exception e)
      {
        System.out.println(
          "An error occured while generating the graphs ("
            + e.getMessage()
            + ")");
      }
    }

    Runtime.getRuntime().exit(0);
  }

  private static Process my_exec(String[] cmd) throws IOException
  {
    Process p = Runtime.getRuntime().exec(cmd);
    final java.io.InputStream err = p.getErrorStream();
    new Thread() {
      public void run() {
        byte[] buf = new byte[128];
        try {
          while (true) {
            int len = err.read(buf);
            if (len == -1) break;
            origStderr.write(buf, 0, len);
          }
        } catch (java.io.IOException exc) {
          exc.printStackTrace(origStderr);
        }
      }
    }.start();
    return p;
  }
}
