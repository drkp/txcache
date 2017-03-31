<?php
// Print the virtual time base stored in a database
define("TXCACHE", false);

$scriptName = "GetVirtualTimeBase.php";
include("PHPprinter.php");
$startTime = getMicroTime();

getDatabaseLink($link);

beginRO($link);
$result = sql_query("SELECT EXTRACT(epoch FROM virtual_time_base) AS virtual_time_base FROM virtual_time_base", $link);
if (sql_num_rows($result) != 1)
  die("Failed to get virtual_time_base");
$row = sql_fetch_array($result);
$base = $row["virtual_time_base"];
sql_free_result($result);

echo "$base\n";

flush();

commit($link);
sql_close($link);
    
//printHTMLfooter($scriptName, $startTime);
?>
