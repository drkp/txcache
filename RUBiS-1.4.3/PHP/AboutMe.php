<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "AboutMe.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $nickname = $HTTP_POST_VARS['nickname'];
    if ($nickname == null)
    {
      $nickname = $HTTP_GET_VARS['nickname'];
      if ($nickname == null)
      {
         printError($scriptName, $startTime, "About me", "You must provide your nick name!<br>");
         exit();
      }
    }
    $password = $HTTP_POST_VARS['password'];
    if ($password == null)
    {
      $password = $HTTP_GET_VARS['password'];
      if ($password == null)
      {
         printError($scriptName, $startTime, "About me", "You must provide your password!<br>");
         exit();
      }
    }
      
    getDatabaseLink($link);

    beginRO($link);
    // Authenticate the user 
    $userId = authenticate($nickname, $password, $link);
    if ( $userId == -1)
    {
      die("<h2>ERROR: You don't have an account on RUBis! You have to register first.</h2><br>");
    }

function getUserBids($link, $userId)
{
    txcache_invaltag("bids", "user_id", $userId);
    $bidsResult = sql_query("SELECT item_id, MAX(max_bid) AS max_bid FROM bids WHERE bids.user_id=$userId  GROUP BY item_id", $link) or die("ERROR: Query failed for getting bids list.");
    $bids = array();

    while ($bidsRow = sql_fetch_array($bidsResult)) {
        $bids[$bidsRow["item_id"]] = $bidsRow["max_bid"];
    }

    sql_free_result($bidsResult);
    
    return $bids;
}

function aboutmeBidAndWon($link, $userId)
{
    $vtime = virtualTime();
    $now = virtualTimeSQL();    

    $bids = wrap(true, 'getUserBids', $link, $userId);

    $activeBidItems = array();
    $won30Items = array();

    foreach ($bids as $itemId => $maxBid) {
        $item = getAnyItem($link, $itemId);
        if (!$item) {
            continue;
        }

        // Stuff our bid in with the rest of the item data
        $item["my_max_bid"] = $maxBid;
        
        $endDate = strtotime($item["end_date"]);
        
        if ($endDate > $vtime) {
            $activeBidItems[] = $item;
        } else if (($vtime - $endDate) < 30*86400) {
            /* XXX(DRKP) This is actually all items *bid* on in the
             * last 30 days, not items won. For whatever reason,
             * that's what RUBiS displays... */
            $won30Items[] = $item;
        }
    }
        
    // Get the items the user has bid on
    if (count($activeBidItems) == 0)
      printHTMLHighlighted("<h2>You did not bid on any item.</h2>\n");
    else
    {
      printHTMLHighlighted("<h3>Items you have bid on.</h3>\n");
      print("<TABLE border=\"1\" summary=\"Items You've bid on\">\n".
                "<THEAD>\n".
                "<TR><TH>Designation<TH>Initial Price<TH>Current price<TH>Your max bid<TH>Quantity".
                "<TH>Start Date<TH>End Date<TH>Seller<TH>Put a new bid\n".
                "<TBODY>\n");
      foreach ($activeBidItems as $itemRow)
      {
	  $maxBid = $itemRow["my_max_bid"];
	  $currentPrice = $itemRow["max_bid"];
	  if ($currentPrice == null)
	    $currentPrice = "none";
	
	  $itemName = $itemRow["name"];
	  $itemInitialPrice = $itemRow["initial_price"];
	  $quantity = $itemRow["quantity"];
	  $itemReservePrice = $itemRow["reserve_price"];
	  $startDate = $itemRow["start_date"];
	  $endDate = $itemRow["end_date"];
	  $sellerId = $itemRow["seller"];

          $sellerRow = getUser($link, $sellerId);
	  if (!$sellerRow)
          {
	    die("<h3>ERROR: This seller does not exist.</h3><br>\n");
          }
	  $sellerNickname = $sellerRow["nickname"];
	  
	  print("<TR><TD><a href=\"/PHP/ViewItem.php?itemId=".$itemId."\">".$itemName.
		"<TD>".$itemInitialPrice."<TD>".$currentPrice."<TD>".$maxBid."<TD>".$quantity.
		"<TD>".$startDate."<TD>".$endDate.
		"<TD><a href=\"/PHP/ViewUserInfo.php?userId=".$sellerId."\">".$sellerNickname.
		"<TD><a href=\"/PHP/PutBid.php?itemId=".$itemId."&nickname=".urlencode($nickname)."&password=".urlencode($password)."\"><IMG SRC=\"/PHP/bid_now.jpg\" height=22 width=90></a>\n");
	
	}
	print("</TBODY></TABLE><p>\n");
    }

     // Get the items the user won in the past 30 days
     if (count($won30Items) == 0)
         printHTMLHighlighted("<h3>You didn't win any item.</h3>\n");
     else
     {
       printHTMLHighlighted("<h3>Items you won in the past 30 days.</h3>\n");
       print("<p><TABLE border=\"1\" summary=\"List of items\">\n".
                "<THEAD>\n".
                "<TR><TH>Designation<TH>Price you bought it<TH>Seller".
                "<TBODY>\n");
       foreach ($won30Items as $itemRow)
       {         
         $currentPrice = $itemRow["max_bid"];
         if ($currentPrice == null)
          $currentPrice = "none";
         $itemName = $itemRow["name"];
         $sellerId = $itemRow["seller"];
         
         $sellerRow = getUser($link, $sellerId);
         if (!$sellerRow)
          {
           die("<h3>ERROR: This seller does not exist.</h3><br>\n");
          }
         $sellerNickname = $sellerRow["nickname"];
         
         print("<TR><TD><a href=\"/PHP/ViewItem.php?itemId=".$itemId."\">".$itemName.
               "<TD>".$currentPrice.
               "<TD><a href=\"/PHP/ViewUserInfo.php?userId=".$sellerId."\">".$sellerNickname.
               "\n");
       }
       
       print("</TBODY></TABLE><p>\n");
     }
}

function aboutmeBuyNow($link, $userId)
{
    $vtime = virtualTime();
    $now = virtualTimeSQL();    

         // Get the items the user bought in the past 30 days
     txcache_invaltag("buy_now", "buyer_id", $userId);
     $buyNowResult = sql_query("SELECT * FROM buy_now WHERE buy_now.buyer_id=$userId AND buy_now.date >= '$now'::date - '30 days'::interval", $link)or die("ERROR: Query failed for getting buy now list.");    
     if (sql_num_rows($buyNowResult) == 0)
       printHTMLHighlighted("<h3>You didn't buy any item in the past 30 days.</h3>\n");
     else
     {
       printHTMLHighlighted("<h3>Items you bought in the past 30 days.</h3>\n");
       print("<p><TABLE border=\"1\" summary=\"List of items\">\n".
             "<THEAD>\n".
             "<TR><TH>Designation<TH>Quantity<TH>Price you bought it<TH>Seller".
             "<TBODY>\n");
       while ($buyNowRow = sql_fetch_array($buyNowResult))
       {
         $itemId = $buyNowRow["item_id"];
         // XXX(Austin) Why did RUBiS assume it would be an old item?
         $itemRow = getAnyItem($link, $itemId);
         if (!$itemRow)
          {
           die("<h3>ERROR: This item does not exist.</h3><br>\n");
          }

         $itemName = $itemRow["name"];
         $sellerId = $itemRow["seller"];
         $price = $itemRow["buy_now"]*$buyNowRow["qty"];
         
         $sellerRow = getUser($link, $sellerId);
         if (!$sellerRow)
          {
           die("<h3>ERROR: This seller does not exist.</h3><br>\n");
          }
         $sellerNickname = $sellerRow["nickname"];
         
         print("<TR><TD><a href=\"/PHP/ViewItem.php?itemId=".$itemId."\">".$itemName.
               "<TD>".$buyNowRow["qty"]."<TD>$price".
               "<TD><a href=\"/PHP/ViewUserInfo.php?userId=".$sellerId."\">".$sellerNickname.
               "\n");
       }
       
       print("</TBODY></TABLE><p>\n");
     }

     sql_free_result($buyNowResult);
}

function aboutmeSeller($link, $userId)
{
    $vtime = virtualTime();
    $now = virtualTimeSQL();    

     // Get the items the user is currently selling
     txcache_invaltag("items", "seller", $userId);
     $currentSellsResult = sql_query("SELECT * FROM items WHERE items.seller=$userId AND items.end_date>='$now'", $link) or die("ERROR: Query failed for getting current sellings.");
     if (sql_num_rows($currentSellsResult) == 0)
      printHTMLHighlighted("<h3>You are currently selling no item.</h3>\n");
     else
     {
       printHTMLHighlighted("<h3>Items you are selling.</h3>\n");
       print("<p><TABLE border=\"1\" summary=\"List of items\">\n".
                "<THEAD>\n".
                "<TR><TH>Designation<TH>Initial Price<TH>Current price<TH>Quantity<TH>ReservePrice<TH>Buy Now".
                "<TH>Start Date<TH>End Date\n".
                "<TBODY>\n");
       while ($currentSellsRow = sql_fetch_array($currentSellsResult))
       {
	   $itemName = $currentSellsRow["name"];
	   $itemInitialPrice = $currentSellsRow["initial_price"];
	   $quantity = $currentSellsRow["quantity"];
	   $itemReservePrice = $currentSellsRow["reserve_price"];
	   $buyNow = $currentSellsRow["buy_now"];
	   $endDate = $currentSellsRow["end_date"];
	   $startDate = $currentSellsRow["start_date"];
	   $itemId = $currentSellsRow["id"];
//	   $currentPriceResult = sql_query("SELECT MAX(bid) AS bid FROM bids WHERE item_id=$itemId", $link) or die("ERROR: Query failed for getting the item current price (sold item).");
//	   if (sql_num_rows($currentPriceResult) == 0)
//	       die ("ERROR: Cannot get the current price (sold item).");
//	   $currentPriceRow = sql_fetch_array($currentPriceResult);
	   $currentPrice = $currentSellsResult["max_bid"]; 
	   if ($currentPrice == null)
	   	$currentPrice = "none";

	   print("<TR><TD><a href=\"/PHP/ViewItem.php?itemId=".$itemId."\">".$itemName.
                  "<TD>".$itemInitialPrice."<TD>".$currentPrice."<TD>".$quantity.
		  "<TD>".$itemReservePrice."<TD>".$buyNow.
                  "<TD>".$startDate."<TD>".$endDate."\n");

//	   sql_free_result($currentPriceResult);
       }
       print("</TABLE><p>\n");
     }

     // Get the items the user sold the last 30 days
     txcache_invaltag("old_items", "seller", $userId);
     $pastSellsResult = sql_query("SELECT * FROM old_items WHERE old_items.seller=$userId AND old_items.end_date > '$now'::date - '30 days'::interval", $link) or die("ERROR: Query failed for getting sold items list.");
     if (sql_num_rows($pastSellsResult) == 0)
      printHTMLHighlighted("<h3>You didn't sell any item in the last 30 days.</h3>\n");
     else
     {
       printHTMLHighlighted("<h3>Items you sold in the last 30 days.</h3>\n");
       print("<p><TABLE border=\"1\" summary=\"List of items\">\n".
                "<THEAD>\n".
                "<TR><TH>Designation<TH>Initial Price<TH>Current price<TH>Quantity<TH>ReservePrice<TH>Buy Now".
                "<TH>Start Date<TH>End Date\n".
                "<TBODY>\n");
       while ($pastSellsRow = sql_fetch_array($pastSellsResult))
       {
	   $itemName = $pastSellsRow["name"];
	   $itemInitialPrice = $pastSellsRow["initial_price"];
	   $quantity = $pastSellsRow["quantity"];
	   $itemReservePrice = $pastSellsRow["reserve_price"];
	   $buyNow = $pastSellsRow["buy_now"];
	   $endDate = $pastSellsRow["end_date"];
	   $startDate = $pastSellsRow["start_date"];
	   $itemId = $pastSellsRow["id"];
// 	   $currentPriceResult = sql_query("SELECT MAX(bid) AS bid FROM bids WHERE item_id=$itemId", $link) or die("ERROR: Query failed for getting the item current price (sold item).");
// 	   if (sql_num_rows($currentPriceResult) == 0)
// 	       die ("ERROR: Cannot get the current price (sold item).");

// 	   $currentPriceRow = sql_fetch_array($currentPriceResult);
	   $currentPrice = $pastSellsResult["max_bid"]; 
	   if ($currentPrice == null)
	   	$currentPrice = "none";

	   print("<TR><TD><a href=\"/PHP/ViewItem.php?itemId=".$itemId."\">".$itemName.
                  "<TD>".$itemInitialPrice."<TD>".$currentPrice."<TD>".$quantity.
		  "<TD>".$itemReservePrice."<TD>".$buyNow.
                  "<TD>".$startDate."<TD>".$endDate."\n");

       }
       print("</TABLE><p>\n");

     }

     sql_free_result($currentSellsResult);
     sql_free_result($pastSellsResult);
}

function aboutmeComments($link, $userId)
{
    $vtime = virtualTime();
    $now = virtualTimeSQL();    

    // Get the comments about the user
    txcache_invaltag("comments", "to_user", $userId);
    $commentsResult = sql_query("SELECT * FROM comments WHERE comments.to_user_id=$userId", $link) or die("ERROR: Query failed for the list of comments.");
    if (sql_num_rows($commentsResult) == 0)
      printHTMLHighlighted("<h2>There is no comment for this user.</h2>\n");
    else
    {
	print("<p><DL>\n");
	printHTMLHighlighted("<h3>Comments about you.</h3>\n");
	while ($commentsRow = sql_fetch_array($commentsResult))
	{
	    $authorId = $commentsRow["from_user_id"];
            $authorRow = getUser($link, $authorId);
	    if (!$authorRow)
            {
              die("ERROR: This author does not exist.<br>\n");
            }
	    else
	    {
		$authorName = $authorRow["nickname"];
	    }
	    $date = $commentsRow["date"];
	    $comment = $commentsRow["comment"];
	    
	    print("<DT><b><BIG><a href=\"/PHP/ViewUserInfo.php?userId=".$authorId."\">$authorName</a></BIG></b>"." wrote the ".$date."<DD><i>".$comment."</i><p>\n");
	}
	print("</DL>\n");
    }

    sql_free_result($commentsResult);
}

function aboutme($link, $userId, $password) {
    $userRow = getUser($link, $userId);
    if (!$userRow)
    {
      die("<h3>ERROR: Sorry, but this user does not exist.</h3><br>\n");
    }

   printHTMLheader("RUBiS: About me");

      // Get general information about the user
    $firstname = $userRow["firstname"];
    $lastname = $userRow["lastname"];
    $nickname = $userRow["nickname"];
    $email = $userRow["email"];
    $creationDate = $userRow["creation_date"];
    $rating = $userRow["rating"];

    printHTMLHighlighted("<h2>Information about ".$nickname."<br></h2>");
    print("Real life name : ".$firstname." ".$lastname."<br>");
    print("Email address  : ".$email."<br>");
    print("User since     : ".$creationDate."<br>");
    print("Current rating : <b>".$rating."</b><br><p>");

    $vtime = virtualTime();
    $now = virtualTimeSQL();    

    wrap(true, 'aboutmeBidAndWon', $link, $userId);
    wrap(true, 'aboutmeBuyNow', $link, $userId);
    wrap(true, 'aboutmeSeller', $link, $userId);
    printCommentTable($link, $userId);
}

//    wrap(true, 'aboutme', $link, $userId, $password);
    aboutme($link, $userId, $password);

    commit($link);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
