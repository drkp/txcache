<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "PutBid.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $nickname = $HTTP_POST_VARS['nickname'];
    if ($nickname == null)
    {
      $nickname = $HTTP_GET_VARS['nickname'];
      if ($nickname == null)
      {
         printError($scriptName, $startTime, "PutBid", "You must provide a nick name!<br>");
         exit();
      }
    }

    $password = $HTTP_POST_VARS['password'];
    if ($password == null)
    {
      $password = $HTTP_GET_VARS['password'];
      if ($password == null)
      {
         printError($scriptName, $startTime, "PutBid", "You must provide a password!<br>");
         exit();
      }
    }

    $itemId = $HTTP_POST_VARS['itemId'];
    if ($itemId == null)
    {
      $itemId = $HTTP_GET_VARS['itemId'];
      if ($itemId == null)
      {
         printError($scriptName, $startTime, "PutBid", "You must provide an item identifier!<br>");
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

function putbid($link, $itemId) {
    $row = getItem($link, $itemId);
    if (!$row)
    {
      global $scriptName, $startTime;
      printError($scriptName, $startTime, "PutBid", "<h3>Sorry, but this item does not exist.</h3><br>");
      exit();
    }

    txcache_invaltag("bids", "item_id", $row["id"]);
    $maxBidResult = sql_query("SELECT MAX(bid) AS bid FROM bids WHERE item_id=".$row["id"], $link) or die("ERROR: Max bid query failed");
    $maxBidRow = sql_fetch_array($maxBidResult);
    $maxBid = $maxBidRow["bid"];
    if ($maxBid == 0)
    {
      $maxBid = $row["initial_price"];
      $buyNow = $row["buy_now"];
      $firstBid = "none";
    }
    else
    {
      if ($row["quantity"] > 1)
      {
        $xRes = sql_query("SELECT bid,qty FROM bids WHERE item_id=".$row["id"]." ORDER BY bid DESC LIMIT ".$row["quantity"], $link) or die("ERROR: Quantity query failed");
        $nb = 0;
        while ($xRow = sql_fetch_array($xRes))
        {
          $nb = $nb + $xRow["qty"];
          if ($nb > $row["quantity"])
          {
            $maxBid = $row["bid"];
            break;
          }
        }
      }
      $firstBid = $maxBid;
      $nbOfBidsResult = sql_query("SELECT COUNT(*) AS bid FROM bids WHERE item_id=".$row["id"], $link) or die("ERROR: Nb of bids query failed");
      $nbOfBidsRow = sql_fetch_array($nbOfBidsResult);
      $nbOfBids = $nbOfBidsRow["bid"];
      sql_free_result($nbOfBidsResult);
    }

    printHTMLheader("RUBiS: Bidding");
    printHTMLHighlighted("You are ready to bid on: ".$row["name"]);
    print("<TABLE>\n".
          "<TR><TD>Currently<TD><b><BIG>$maxBid</BIG></b>\n");    

    // Check if the reservePrice has been met (if any)
    $reservePrice = $row["reserve_price"];
    if ($reservePrice > 0)
    {
	if ($maxBid >= $reservePrice)
	{
	  print("(The reserve price has been met)\n");
	}
	else
	{
          print("(The reserve price has NOT been met)\n");
	}
    }

    txcache_invaltag("users", "id", $row["seller"]);
    $sellerNameResult = sql_query("SELECT users.nickname FROM users WHERE id=".$row["seller"], $link) or die("ERROR: Seller name query failed");
    $sellerNameRow = sql_fetch_array($sellerNameResult);
    $sellerName = $sellerNameRow["nickname"];
    sql_free_result($sellerNameResult);

    print("<TR><TD>Quantity<TD><b><BIG>".$row["quantity"]."</BIG></b>\n");
    print("<TR><TD>First bid<TD><b><BIG>$firstBid</BIG></b>\n");
    print("<TR><TD># of bids<TD><b><BIG>$nbOfBids</BIG></b> (<a href=\"/PHP/ViewBidHistory.php?itemId=".$row["id"]."\">bid history</a>)\n");
    print("<TR><TD>Seller<TD><a href=\"/PHP/ViewUserInfo.php?userId=".$row["seller"]."\">$sellerName</a> (<a href=\"/PHP/PutCommentAuth.php?to=".$row["seller"]."&itemId=".$row["id"]."\">Leave a comment on this user</a>)\n");
    print("<TR><TD>Started<TD>".$row["start_date"]."\n");
    print("<TR><TD>Ends<TD>".$row["end_date"]."\n");
    print("</TABLE>\n");

    // Can the user by this item now ?
    if (isset($buynow) && $buyNow > 0)
	print("<p><a href=\"/PHP/BuyNowAuth.php?itemId=".$row["id"]."\">".
              "<IMG SRC=\"/PHP/buy_it_now.jpg\" height=22 width=150></a>".
              "  <BIG><b>You can buy this item right now for only \$$buyNow</b></BIG><br><p>\n");

    printHTMLHighlighted("Item description");
    print($row["description"]);
    print("<br><p>\n");

    printHTMLHighlighted("Bidding");
    $minBid = $maxBid+1;
    $out = '';
    $out .= ("<form action=\"/PHP/StoreBid.php\" method=POST>\n".
          "<input type=hidden name=minBid value=$minBid>\n".
          "<input type=hidden name=userId value=\$XXXuserIdXXX>\n".
          "<input type=hidden name=itemId value=".$row["id"].">\n".
          "<input type=hidden name=maxQty value=".$row["quantity"].">\n".
          "<center><table>\n".
          "<tr><td>Your bid (minimum bid is $minBid):</td>\n".
          "<td><input type=text size=10 name=bid></td></tr>\n".
          "<tr><td>Your maximum bid:</td>\n".
          "<td><input type=text size=10 name=maxBid></td></tr>\n");
    if ($row["quantity"] > 1)
      $out .= ("<tr><td>Quantity:</td><td><input type=text size=5 name=qty></td></tr>\n");
    else
      $out .= ("<input type=hidden name=qty value=1>\n");
    $out .= ("</table><p><input type=submit value=\"Bid now!\"></center><p>\n");

    sql_free_result($maxBidResult);
    return $out;
}

$page = wrap(true, 'putbid', $link, $itemId);
    echo str_replace('$XXXuserIdXXX', $userId, $page);

    commit($link);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
