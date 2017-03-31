<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "BuyNow.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $nickname = $HTTP_POST_VARS['nickname'];
    if ($nickname == null)
    {
      $nickname = $HTTP_GET_VARS['nickname'];
      if ($nickname == null)
      {
         printError($scriptName, $startTime, "BuyNow", "You must provide a nick name!<br>");
         exit();
      }
    }

    $password = $HTTP_POST_VARS['password'];
    if ($password == null)
    {
      $password = $HTTP_GET_VARS['password'];
      if ($password == null)
      {
         printError($scriptName, $startTime, "BuyNow", "You must provide a password!<br>");
         exit();
      }
    }

    $itemId = $HTTP_POST_VARS['itemId'];
    if ($itemId == null)
    {
      $itemId = $HTTP_GET_VARS['itemId'];
      if ($itemId == null)
      {
         printError($scriptName, $startTime, "BuyNow", "You must provide an item identifier!<br>");
         exit();
      }
    }

    getDatabaseLink($link);

    beginRO($link);
    // Authenticate the user
    $userId = authenticate($nickname, $password, $link);
    if ($userId == -1)
    {
      printError($scriptName, $startTime, "Authentication", "You don't have an account on RUBiS!<br>You have to register first.<br>\n");
      exit();
    }

function buynow($link, $itemId, $userId) {
    $row = getItem($link, $itemId);
    if (!$row)
    {
      global $scriptName, $startTime;
      printError($scriptName, $startTime, "BuyNow", "<h3>ERROR: Sorry, but this item does not exist.</h3><br>");
      exit();
    }

    $sellerNameRow = getUser($link, $userId);
    $sellerName = $sellerNameRow["nickname"];

    printHTMLheader("RUBiS: Buy Now");
    printHTMLHighlighted("You are ready to buy this item: ".$row["name"]);
    print("<TABLE>\n");
    print("<TR><TD>Quantity<TD><b><BIG>".$row["quantity"]."</BIG></b>\n");
    print("<TR><TD>Seller<TD><a href=\"/PHP/ViewUserInfo.php?userId=".$row["seller"]."\">$sellerName</a> (<a href=\"/PHP/PutCommentAuth.php?to=".$row["seller"]."&itemId=".$row["id"]."\">Leave a comment on this user</a>)\n");
    print("<TR><TD>Started<TD>".$row["start_date"]."\n");
    print("<TR><TD>Ends<TD>".$row["end_date"]."\n");
    print("</TABLE>\n");

    printHTMLHighlighted("Item description");
    print($row["description"]);
    print("<br><p>\n");

    printHTMLHighlighted("Buy Now");
    print("<form action=\"/PHP/StoreBuyNow.php\" method=POST>\n".
          "<input type=hidden name=userId value=$userId>\n".
          "<input type=hidden name=itemId value=".$row["id"].">\n".
          "<input type=hidden name=maxQty value=".$row["quantity"].">\n");
    if ($row["quantity"] > 1)
      print("<center><table><tr><td>Quantity:</td><td><input type=text size=5 name=qty></td></tr></table></center>\n");
    else
      print("<input type=hidden name=qty value=1>\n");
    print("</table><p><center><input type=submit value=\"Buy now!\"></center><p>\n");
}

if (MICROCACHE)
  buynow($link, $itemId, $userId);
else
    wrap(true, 'buynow', $link, $itemId, $userId);

    commit($link);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
