<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "RegisterUser.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $firstname = $HTTP_POST_VARS['firstname'];
    if ($firstname == null)
    {
      $firstname = $HTTP_GET_VARS['firstname'];
      if ($firstname == null)
      {
         printError($scriptName, $startTime, "Register user", "You must provide a first name!<br>");
         exit();
      }
    }
      
    $lastname = $HTTP_POST_VARS['lastname'];
    if ($lastname == null)
    {
      $lastname = $HTTP_GET_VARS['lastname'];
      if ($lastname == null)
      {
         printError($scriptName, $startTime, "Register user", "You must provide a last name!<br>");
         exit();
      }
    }
      
    $nickname = $HTTP_POST_VARS['nickname'];
    if ($nickname == null)
    {
      $nickname = $HTTP_GET_VARS['nickname'];
      if ($nickname == null)
      {
         printError($scriptName, $startTime, "Register user", "You must provide a nick name!<br>");
         exit();
      }
    }

    $email = $HTTP_POST_VARS['email'];
    if ($email == null)
    {
      $email = $HTTP_GET_VARS['email'];
      if ($email == null)
      {
         printError($scriptName, $startTime, "Register user", "You must provide an email address!<br>");
         exit();
      }
    }

    $password = $HTTP_POST_VARS['password'];
    if ($password == null)
    {
      $password = $HTTP_GET_VARS['password'];
      if ($password == null)
      {
         printError($scriptName, $startTime, "Register user", "You must provide a password!<br>");
         exit();
      }
    }

    $region = $HTTP_POST_VARS['region'];
    if ($region == null)
    {
      $region = $HTTP_GET_VARS['region'];
      if ($region == null)
      {
         printError($scriptName, $startTime, "Register user", "You must provide a region!<br>");
         exit();
      }
    }

    getDatabaseLink($link);

    beginRW($link);
    // Check if the region really exists
    $regionResult = sql_query("SELECT * FROM regions WHERE name=\"$region\"", $link) or die("ERROR: Region query failed");
    if (sql_num_rows($regionResult) == 0)
    {
      printError($scriptName, $startTime, "Register user", "Region $region does not exist in the database!<br>\n");
      sql_free_result($regionResult);
      exit();
    }
    else
    {
      $regionRow = sql_fetch_array($regionResult);
      $regionId = $regionRow["id"];
      sql_free_result($regionResult);
    }

    // Check if the nick name already exists
    $nicknameResult = sql_query("SELECT * FROM users WHERE nickname=\"$nickname\"", $link) or die("ERROR: Nickname query failed");
    if (sql_num_rows($nicknameResult) > 0)
    {
      printError($scriptName, $startTime, "Register user", "The nickname you have choosen is already taken by someone else. Please choose a new nickname.<br>\n");
      sql_free_result($nicknameResult);
      exit();
    }
    sql_free_result($nicknameResult);

    // Add user to database
    $now = virtualTimeSQL();
    $result = sql_query("INSERT INTO users VALUES ($ID_DEFAULT, \"$firstname\", \"$lastname\", \"$nickname\", \"$password\", \"$email\", 0, 0, \"$now\", $regionId)", $link) or die("ERROR: Failed to insert new user in database.");

    $result = sql_query("SELECT * FROM users WHERE nickname=\"$nickname\"", $link) or die("ERROR: Query user failed");
    $row = sql_fetch_array($result);
txcache_inval("users", "id", $row["id"]);
txcache_inval("users", "nickname", $nickname);
txcache_inval("users", "region", $regionId);
    commit($link);

    printHTMLheader("RUBiS: Welcome to $nickname");
    print("<h2>Your registration has been processed successfully</h2><br>\n");
    print("<h3>Welcome $nickname</h3>\n");
    print("RUBiS has stored the following information about you:<br>\n");
    print("First Name : ".$row["firstname"]."<br>\n");
    print("Last Name  : ".$row["lastname"]."<br>\n");
    print("Nick Name  : ".$row["nickname"]."<br>\n");
    print("Email      : ".$row["email"]."<br>\n");
    print("Password   : ".$row["password"]."<br>\n");
    print("Region     : $region<br>\n"); 
    print("<br>The following information has been automatically generated by RUBiS:<br>\n");
    print("User id       :".$row["id"]."<br>\n");
    print("Creation date :".$row["creation_date"]."<br>\n");
    print("Rating        :".$row["rating"]."<br>\n");
    print("Balance       :".$row["balance"]."<br>\n");
    
    sql_free_result($result);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>