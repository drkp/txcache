<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "StoreBuyNow.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $userId = $HTTP_POST_VARS['userId'];
    if ($userId == null)
    {
      $userId = $HTTP_GET_VARS['userId'];
      if ($userId == null)
      {
         printError($scriptName, $startTime, "StoreBuyNow", "<h3>You must provide a user identifier!<br></h3>");
         exit();
      }
    }
      
    $itemId = $HTTP_POST_VARS['itemId'];
    if ($itemId == null)
    {
      $itemId = $HTTP_GET_VARS['itemId'];
      if ($itemId == null)
      {
         printError($scriptName, $startTime, "StoreBuyNow", "<h3>You must provide an item identifier !<br></h3>");
         exit();
      }
    }
      
    $maxQty = $HTTP_POST_VARS['maxQty'];
    if ($maxQty == null)
    {
      $maxQty = $HTTP_GET_VARS['maxQty'];
      if ($maxQty == null)
      {
         printError($scriptName, $startTime, "StoreBuyNow", "<h3>You must provide a maximum quantity !<br></h3>");
         exit();
      }
    }

    $qty = $HTTP_POST_VARS['qty'];
    if ($qty == null)
    {
      $qty = $HTTP_GET_VARS['qty'];
      if ($qty == null)
      {
         printError($scriptName, $startTime, "StoreBuyNow", "<h3>You must provide a quantity !<br></h3>");
         exit();
      }
    }

    /* Check for invalid values */

    if ($qty > $maxQty)
    {
      printError("<h3>You cannot request $qty items because only $maxQty are proposed !<br></h3>");
      return ;
    }      

    getDatabaseLink($link);
    beginRW($link);

    if (!$postgres)
      sql_query("LOCK TABLES buy_now WRITE, items WRITE", $link) or die("ERROR: Failed to acquire locks on items and buy_now tables.");
    $result = sql_query("SELECT * FROM items WHERE items.id=$itemId", $link) or die("ERROR: Query failed");
    if (sql_num_rows($result) == 0)
    {
      printError($scriptName, $startTime, "BuyNow", "<h3>Sorry, but this item does not exist.</h3><br>");
      exit();
    }
    $row = sql_fetch_array($result);
    $newQty = $row["quantity"]-$qty;

    $now = virtualTimeSQL();

    txcache_inval("items", "id", $itemId);
    txcache_inval("items", "category", $row["category"]);
    txcache_inval("items", "seller", $row["seller"]);

    if ($newQty == 0)
      sql_query("UPDATE items SET end_date='$now',quantity=$newQty WHERE id=$itemId", $link) or die("ERROR: Failed to update item");
    else
      sql_query("UPDATE items SET quantity=$newQty WHERE id=$itemId", $link) or die("ERROR: Failed to update item");
    // Add BuyNow to database
    $result = sql_query("INSERT INTO buy_now VALUES ($ID_DEFAULT, $userId, $itemId, $qty, \"$now\") RETURNING id", $link) or die("ERROR: Failed to insert new BuyNow in database.");
    $row = sql_fetch_array($result);
    txcache_inval("buy_now", "id", $row["id"]);
    txcache_inval("buy_now", "item_id", $itemId);
    txcache_inval("buy_now", "buyer_id", $userId);
    if (!$postgres)
      sql_query("UNLOCK TABLES", $link) or die("ERROR: Failed to unlock items and buy_now tables.");

    printHTMLheader("RUBiS: BuyNow result");
    if ($qty == 1)
      print("<center><h2>Your have successfully bought this item.</h2></center>\n");
    else
      print("<center><h2>Your have successfully bought these items.</h2></center>\n");
    
    sql_free_result($result);
    commit($link);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
