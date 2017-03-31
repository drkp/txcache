<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
    $scriptName = "ViewBidHistory.php";
    include("PHPprinter.php");
    $startTime = getMicroTime();
    
    $itemId = $HTTP_POST_VARS['itemId'];
    if ($itemId == null)
    {
      $itemId = $HTTP_GET_VARS['itemId'];
      if ($itemId == null)
      {
         printError($scriptName, $startTime, "Bid history", "You must provide an item identifier!<br>");
         exit();
      }
    }
      
    getDatabaseLink($link);
    beginRO($link);

function getItemBids($link, $itemId)
{
    txcache_invaltag("bids", "item_id", $itemId);
    $bidsListResult = sql_query("SELECT * FROM bids WHERE item_id=$itemId ORDER BY date DESC", $link) or die("ERROR: Bids list query failed");
    $bids = array();
    
    while ($bidsListRow = sql_fetch_array($bidsListResult)) {
        $bids[] = $bidsListRow;
    }

    sql_free_result($bidsListResult);
    return $bids;
}

function viewbidhistory($link, $itemId) {
    // Get the item name
    $itemNameRow = getAnyItem($link, $itemId);
    if (!$itemNameRow)
    {
      die("<h3>ERROR: Sorry, but this item does not exist.</h3><br>\n");
    }
    $itemName = $itemNameRow["name"];

    
    // Get the list of bids for this item
    $bids = wrap(true, 'getItemBids', $link, $itemId);
    if (count($bids) == 0)
      print ("<h2>There is no bid for $itemName. </h2><br>");
    else
      print ("<h2><center>Bid history for $itemName</center></h2><br>");

    printHTMLheader("RUBiS: Bid history for $itemName.");
    print("<TABLE border=\"1\" summary=\"List of bids\">\n".
                "<THEAD>\n".
                "<TR><TH>User ID<TH>Bid amount<TH>Date of bid\n".
                "<TBODY>\n");

    foreach ($bids as $bidsListRow)
    {
    	$bidAmount = $bidsListRow["bid"];
    	$bidDate = $bidsListRow["date"];
    	$userId = $bidsListRow["user_id"];
	// Get the bidder nickname	
    	if ($userId != 0)
	{
          $userNameRow = getUser($link, $userId);
	  $nickname = $userNameRow["nickname"];
    	}
    	else
	  {
	    print("Cannot lookup the user!<br>");
	    printHTMLfooter($scriptName, $startTime);
	    exit();
	  }
        print("<TR><TD><a href=\"/PHP/ViewUserInfo.php?userId=".$userId."\">$nickname</a>"
		  ."<TD>".$bidAmount."<TD>".$bidDate."\n");
    }
    print("</TABLE>\n");

    sql_free_result($bidsListResult);
}

wrap(true, 'viewbidhistory', $link, $itemId);

    commit($link);
    sql_close($link);
    
    printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
