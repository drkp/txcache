<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "ViewUserInfo.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $userId = $HTTP_POST_VARS['userId'];
    if ($userId == null)
    {
      $userId = $HTTP_GET_VARS['userId'];
      if ($userId == null)
      {
         printError($scriptName, $startTime, "Viewing user information", "You must provide an item identifier!<br>");
         exit();
      }
    }
      
    getDatabaseLink($link);
    beginRO($link);

function viewuserinfo($link, $userId) {
    txcache_invaltag("users", "id", $userId);
    $userResult = sql_query("SELECT * FROM users WHERE users.id=$userId", $link) or die("ERROR: Query failed");
    if (sql_num_rows($userResult) == 0)
    {
      die("<h3>ERROR: Sorry, but this user does not exist.</h3><br>\n");
    }

    printHTMLheader("RUBiS: View user information");

      // Get general information about the user
    $userRow = sql_fetch_array($userResult);
    $firstname = $userRow["firstname"];
    $lastname = $userRow["lastname"];
    $nickname = $userRow["nickname"];
    $email = $userRow["email"];
    $creationDate = $userRow["creation_date"];
    $rating = $userRow["rating"];

    print("<h2>Information about ".$nickname."<br></h2>");
    print("Real life name : ".$firstname." ".$lastname."<br>");
    print("Email address  : ".$email."<br>");
    print("User since     : ".$creationDate."<br>");
    print("Current rating : <b>".$rating."</b><br>");

    printCommentTable($link, $userId);
    
    sql_free_result($userResult);
}

wrap(true, 'viewuserinfo', $link, $userId);

    commit($link);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
