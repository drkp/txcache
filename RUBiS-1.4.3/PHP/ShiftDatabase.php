<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <body>
    <?php
 // Rebase the timestamps of a previously initialized, but unused
 // database so that it's like initDB *just* finished.
define("TXCACHE", false);
define("DBCONNSTRING", "user=cecchet dbname=rubis host=localhost");

$scriptName = "ShiftDatabase.php";
include("PHPprinter.php");
$startTime = getMicroTime();

getDatabaseLink($link);

beginRW($link);
$result = sql_query("SELECT NOW() - MAX(start_date) AS shift FROM items", $link);
if (sql_num_rows($result) != 1)
  die("Failed to get start_time from items");
$row = sql_fetch_array($result);
$shift = $row["shift"];
sql_free_result($result);

echo "Shifting database up $shift<br>";
flush();

function do_shift($link, $shift, $table, $col1, $col2 = null)
{
  echo "Shifting $table...";
  flush();
  $sql = "UPDATE $table SET $col1 = $col1 + INTERVAL '$shift'";
  if ($col2)
    $sql = $sql . ", $col2 = $col2 + INTERVAL '$shift'";
  $result = sql_query($sql, $link) or die("UPDATE of $table failed");
  $nrows = pg_affected_rows($result);
  echo " $nrows affected<br>";
  flush();
  sql_free_result($result);
}

#do_shift($link, $shift, "users", "creation_date");
do_shift($link, $shift, "items", "end_date");
do_shift($link, $shift, "old_items", "start_date", "end_date");
#do_shift($link, $shift, "bids", "date");
#do_shift($link, $shift, "comments", "date");
#do_shift($link, $shift, "buy_now", "date");

function check_empty($link, $table, $pred)
{
  echo "Checking no $pred in table $table...";
  flush();
  $sql = "SELECT COUNT(*) AS c FROM $table WHERE $pred";
  $result = sql_query($sql, $link) or die("SELECT of $table failed");
  if (sql_num_rows($result) != 1)
    die("Failed to get count from $table");
  $row = sql_fetch_array($result);
  $c = $row["c"];
  if ($c != 0)
    die("Check that no $pred on table $table failed.  Got $c rows.");
  echo " Passed<br>";
  flush();
  sql_free_result($result);
}

check_empty($link, "users", "creation_date > now()");
check_empty($link, "items", "start_date > now()");

# this invariant may *not* hold on a primed database: some auctions will
#be ended by buy_now
#check_empty($link, "items", "end_date < now()");

# the old_items invariants should still hold, but they're expensive to check!
#check_empty($link, "old_items", "start_date > now()");
#check_empty($link, "old_items", "end_date > now()");
check_empty($link, "bids", "date > now()");
check_empty($link, "comments", "date > now()");
check_empty($link, "buy_now", "date > now()");

commit($link);
sql_close($link);
    
printHTMLfooter($scriptName, $startTime);
    ?>
  </body>
</html>
