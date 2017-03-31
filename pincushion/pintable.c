// -*- c-file-style: "bsd"; indent-tabs-mode: t-*-

#include "pintable.h"

#include <sys/time.h>

#include "lib/message.h"
#include "lib/timeval.h"
#include "lib/reactor.h"

static pin_t pendingUnpins[MAX_PINS];
static int nPendingUnpins;
static bool unpinInFlight;
static Reactor_Entry_t *dbReactor;
static struct timeval maxStaleness;

static void PTOnRead(struct Reactor_Entry_t *re, int fd, void *opaque);
static void PTOnWrite(struct Reactor_Entry_t *re, int fd, void *opaque);

void PT_Init(struct timeval initMaxStaleness)
{
	head = table;
	tail = table;
	last_ref = table;
	maxStaleness = initMaxStaleness;

	int ps = PQsocket(db_conn);
	if (ps <= 0)
		Panic("Got invalid Postgres socket %d", ps);
	dbReactor = Reactor_Add(ps, PTOnRead, PTOnWrite, NULL);
	if (!dbReactor)
		Panic("Failed to add Postgres connection to reactor");
}

static void
PTOnRead(struct Reactor_Entry_t *re, int fd, void *opaque)
{
	if (!PQconsumeInput(db_conn)) {
		Warning("Failed to consume input from DB: %s",
			PQerrorMessage(db_conn));
		if (PQstatus(db_conn) != CONNECTION_OK) {
			Reactor_Close(re);
			// XXX It would be nice if we could wipe the
			// table and wait for the DB to come back, but
			// we also need to clear the cache in this
			// situation.
			Panic("Lost database connection");
		}
		return;
	}

	PGresult *r = (PGresult*)-1;
	// Does !PQisBusy indicate we can get _all_ the results?
	while (!PQisBusy(db_conn) && (r = PQgetResult(db_conn))) {
		if (PQresultStatus(r) != PGRES_COMMAND_OK) {
			Warning("Unpin failed: %s", PQerrorMessage(db_conn));
		}
		PQclear(r);
	}
	if (!r) {
		// All done with the last unpin batch
		unpinInFlight = false;
		if (nPendingUnpins) {
			Reactor_MarkWritable(dbReactor);
		}
	}
}

static void
PTOnWrite(struct Reactor_Entry_t *re, int fd, void *opaque)
{
	static int sqlLen;
	if (sqlLen == 0)
		sqlLen = strlen("UNPIN XXXXXXXXXXXXXXXXXXXXX; ");

	// Build SQL command
	char *buf = malloc(nPendingUnpins * sqlLen + 1);
	if (!buf)
		Panic("Failed to allocate unpin SQL buffer");
	char *pos = buf;
	for (int i = 0; i < nPendingUnpins; ++i) {
		Notice("Unpinning %d", pendingUnpins[i]);
		sprintf(pos, "UNPIN %d; ", pendingUnpins[i]);
		pos += strlen(pos);
	}

	PQsetnonblocking(db_conn, 1);
	if (PQsendQuery(db_conn, buf)) {
		// Success
		nPendingUnpins = 0;
		unpinInFlight = true;
	} else {
		Warning("Failed to send unpin commands: %s",
			PQerrorMessage(db_conn));
		if (PQstatus(db_conn) == CONNECTION_OK) {
			// XXX This may be wrong
			Notice("Re-queuing DB connection for write");
			Reactor_MarkWritable(dbReactor);
		}
	}
}

void PT_RemoveTail(void)
{
	Assert(tail != head);

	tail = add(tail, 1);
	Assert(tail->ref == 0);
	Notice("Queuing unpin %d", tail->pin);
	Assert(nPendingUnpins < MAX_PINS);
	if (nPendingUnpins == 0 && !unpinInFlight) {
		Reactor_MarkWritable(dbReactor);
	}
	pendingUnpins[nPendingUnpins++] = tail->pin;
}

void
PT_Clean(void) 
{
	struct timeval now;
	gettimeofday(&now, NULL);
        struct timeval bound;
	timersub(&now,&maxStaleness,&bound);
        PT_Entry_t *next = tail;
        
	//starting at the tail, the oldest pins
	//look at each pin
        while(next != head) {
                next = add(next,1);
		//if there is a reference to the pin, we must keep it
		//and everything newer
                if (next->ref > 0) {
                        last_ref = next;
                        break;
                } else if (timercmp(&(next->timestamp), &bound, <)) {
			//if there is no reference to pin and the pin is 
			//older than the maximum staleness, get rid of it.
			PT_RemoveTail();
			Assert(tail == next);
                }
        }
}

PT_Entry_t*
PT_FindPin(pin_t pin)
{
	PT_Entry_t *r;
	for (r = head; r != tail; r = sub(r,1)) {
		if (r->pin == pin) {
			return r;
		}
	}
	return NULL;
}

PT_Entry_t*
PT_Insert(struct timeval timestamp, pin_t pin) 
{
	//If there isn't any room, try cleaning out the old pins
        if (ptr_sub(head,tail) == MAX_PINS-1)
		PT_Clean();

	// If there still isn't room, remove a non-stale pin
        if (ptr_sub(head,tail) == MAX_PINS-1) {
		if ((add(tail, 1))->ref > 0)
			Panic("Pin table is full");
		Warning("Removing non-stale pin");
		PT_RemoveTail();
	}

	{
		PT_Entry_t *r = PT_FindPin(pin);
		if (r) {
			PT_Entry_t *prev = sub(r,1);
			PT_Entry_t *next = add(r,1);
			if (prev != tail)
				Assert(timercmp(&(prev->timestamp),&timestamp,<) ||
				       timercmp(&(prev->timestamp),&timestamp,==));
			if (r != head)
				Assert(timercmp(&timestamp, &(next->timestamp),<) ||
				       timercmp(&timestamp, &(next->timestamp),==));
			r->timestamp = timestamp;
			return r;
		} else {
		
			PT_Entry_t *a, *b;
			
			//put the new entry at the beginning and bubble sort into place
			head = add(head,1);
			head->pin = pin;
			head->timestamp = timestamp;
			head->ref = 0;
			a = sub(head,1);
			b = head;
			
			while(a != tail) {
				if (timercmp(&(a->timestamp), &(b->timestamp),<)) {
					break;
				} else {
					//swap and move
					PT_Entry_t tmp = *b;
					*b = *a;
					*a = tmp;
					b = a;
					a = sub(a,1);
				}
				
			}
			return b;
		}                                
        }
}



PT_Entry_t*
PT_Find(struct timeval lower_bound, int *num_entries)
{
	PT_Entry_t *r;
	struct timeval now;
	gettimeofday(&now, NULL);
        struct timeval bound;
	timersub(&now,&lower_bound,&bound);
        

        //if the latest pin in the table is too old or there aren't any pins
        if ((head == tail) || (timercmp(&(head->timestamp),&bound, <))) {
		*num_entries = 0;
                r = NULL;
        } else {
                PT_Entry_t *a, *b;
		int num = 1;
	        a = sub(head,1);
                b = head;
                while(a != tail) {
                        if (timercmp(&(a->timestamp),&bound,<)) {
                                break;
                        }
			num++;
                        b = a;
                        a = sub(a,1);                        
                }
		*num_entries = num;
                r = b;
        }
	return r;
}

PT_Entry_t*
PT_GetEntry(int i) {
	if (i < ptr_sub(head,tail)) {
		return sub(head,i);
	} else {
		Warning("Referencing invalid entry");
		return NULL;
	}
}

void
PT_AddRef(PT_Entry_t *e) 
{
        Debug("Referencing " FMT_PT_ENTRY, XVA_PT_ENTRY(e));
        e->ref++;
        if(ptr_sub(e,tail) < ptr_sub(last_ref,tail)) {
                last_ref = e;
        }
}

void
PT_RemoveRef(PT_Entry_t *e) 
{ 
        Debug("De-referencing " FMT_PT_ENTRY, XVA_PT_ENTRY(e));
        if (e->ref < 1) 
                Warning("Dereferencing a pin with no references\n");
	else
		e->ref--;
        PT_Clean();
}

void
PT_Flush() 
{
	while (tail != head) {
		PT_RemoveTail();
	}

	last_ref = head;
	Debug("Removed all pins from the pin cushion");
}

void
PT_Print()
{
        int n = 0;
	if (tail == head) {
		printf("No pins in the pin cushion.\n");
	} else {
		printf("Pins in the pin cushion (Newest first)\n");
		printf("===================================\n");
		printf("Pin # \tTimestamp\t\tRef\n");
		PT_Entry_t *e;
		for (e = head; e != tail; e = sub(e,1)) {
			printf("%d\t%ld.%ld\t%d\n",
			       e->pin, e->timestamp.tv_sec, 
			       e->timestamp.tv_usec,e->ref);
                        n++;
		}
		printf("===================================\n");
		printf("%d pins in the pin cushion\n", n);
	}
	fflush(stdout);
}
