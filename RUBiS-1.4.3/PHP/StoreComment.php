<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "StoreComment.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $to = $HTTP_POST_VARS['to'];
    if ($to == null)
    {
      $to = $HTTP_GET_VARS['to'];
      if ($to == null)
      {
         printError($scriptName, $startTime, "PutComment", "You must provide a 'to user' identifier!<br>");
         exit();
      }
    }      

    $from = $HTTP_POST_VARS['from'];
    if ($from == null)
    {
      $from = $HTTP_GET_VARS['from'];
      if ($from == null)
      {
         printError($scriptName, $startTime, "PutComment", "You must provide a 'from user' identifier!<br>");
         exit();
      }
    }

    $itemId = $HTTP_POST_VARS['itemId'];
    if ($itemId == null)
    {
      $itemId = $HTTP_GET_VARS['itemId'];
      if ($itemId == null)
      {
         printError($scriptName, $startTime, "PutComment", "You must provide an item identifier!<br>");
         exit();
      }
    }

    $rating = $HTTP_POST_VARS['rating'];
    if ($rating == null)
    {
      $rating = $HTTP_GET_VARS['rating'];
      if ($rating == null)
      {
         printError($scriptName, $startTime, "StoreComment", "<h3>You must provide a user identifier!<br></h3>");
         exit();
      }
    }
      
    $comment = $HTTP_POST_VARS['comment'];
    if ($comment == null)
    {
      $comment = $HTTP_GET_VARS['comment'];
      if ($comment == null)
      {
         printError($scriptName, $startTime, "StoreComment", "<h3>You must provide a comment !<br></h3>");
         exit();
      }
    }

    getDatabaseLink($link);
    beginRW($link);

    if (!$postgres)
      sql_query("LOCK TABLES users WRITE, comments WRITE", $link) or die("ERROR: Failed to acquire locks on users and comments tables.");
    // Update user rating
    $toRes = sql_query("SELECT * FROM users WHERE id=\"$to\"", $link) or die("ERROR: User query failed");
    if (sql_num_rows($toRes) == 0)
    {
      printError($scriptName, $startTime, "StoreComment", "<h3>Sorry, but this user does not exist.</h3><br>");
      exit();
    }
    $userRow = sql_fetch_array($toRes);
    $rating = $rating + $userRow["rating"];
txcache_inval("users", "id", $to);
txcache_inval("users", "nickname", $userRow["nickname"]);
txcache_inval("users", "region", $userRow["region"]);
    sql_query("UPDATE users SET rating=$rating WHERE id=$to", $link) or die("ERROR: Unable to update user's rating\n");

    // Add bid to database
    $now = virtualTimeSQL();
    $result = sql_query("INSERT INTO comments VALUES ($ID_DEFAULT, $from, $to, $itemId, $rating, \"$now\", \"$comment\") RETURNING id", $link) or die("ERROR: Failed to insert new comment in database.");
$row = sql_fetch_array($result);
txcache_inval("comments", "id", $row["id"]);
txcache_inval("comments", "item", $itemId);
txcache_inval("comments", "to_user", $to);
txcache_inval("comments", "from_user", $from);
    if (!$postgres)
      sql_query("UNLOCK TABLES", $link) or die("ERROR: Failed to unlock users and comments tables.");
    commit($link);

    printHTMLheader("RUBiS: Comment posting");
    print("<center><h2>Your comment has been successfully posted.</h2></center>\n");
    
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
