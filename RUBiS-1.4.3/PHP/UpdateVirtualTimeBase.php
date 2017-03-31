<?php
// Set the virtual time base in the database to whatever the current
// virtual time is (according to the config file)
define("TXCACHE", false);

$scriptName = "UpdateVirtualTimeBase.php";
include("PHPprinter.php");
$startTime = getMicroTime();

getDatabaseLink($link);

$base = virtualTimeSQL();

echo "Setting virtual time base to $base\n";


beginRW($link);

$result = sql_query("DELETE FROM virtual_time_base", $link);
sql_free_result($result);

$result = sql_query("INSERT INTO virtual_time_base (virtual_time_base) VALUES ('$base')", $link);
sql_free_result($result);

$result = sql_query("SELECT EXTRACT(epoch FROM virtual_time_base) AS virtual_time_base FROM virtual_time_base", $link);
if (sql_num_rows($result) != 1)
  die("Failed to get virtual_time_base");
$row = sql_fetch_array($result);
$epoch = $row["virtual_time_base"];
sql_free_result($result);

echo "Updated virtual time base to $epoch\n";

flush();

commit($link);
sql_close($link);
    
//printHTMLfooter($scriptName, $startTime);
?>
