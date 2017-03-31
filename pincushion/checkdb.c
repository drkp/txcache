// -*- c-file-style: "bsd"; indent-tabs-mode: t-*-

#include "checkdb.h"

#include "lib/dbconn.h"
#include "lib/message.h"
#include <stdlib.h>
#include <string.h>

static void
CheckDBSetting(const char *setting, const char *value)
{
	char *buf = malloc(strlen(setting) + 5 + 1);
	if (!buf)
		Panic("Failed to allocate buffer");
	sprintf(buf, "show %s", setting);

	PGresult *res;
	res = PQexec(db_conn, buf);
	if (PQresultStatus(res) != PGRES_TUPLES_OK) {
		Panic("Failed to query %s setting: %s", setting,
		      PQerrorMessage(db_conn));
	}
	if (PQntuples(res) != 1) {
		Panic("Expected one tuple, got %d", PQntuples(res));
	}
	char *val = PQgetvalue(res, 0, 0);
	if (strcmp(val, value)) {
		Panic("'%s' must be '%s'", setting, value);
	}
	PQclear(res);
	free(buf);
}

void
CheckDB_CheckSettings(void)
{
	CheckDBSetting("default_transaction_isolation", "serializable");
	CheckDBSetting("enable_bitmapscan", "off");
	CheckDBSetting("enable_tidscan", "off");
}
