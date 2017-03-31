// -*- c-file-style: "bsd" -*-

#ifndef _PINCUSHION_PINTABLE_H_
#define _PINCUSHION_PINTABLE_H_

#include <stdio.h>
#include <stdbool.h>
#include <sys/time.h>
#include "lib/interval.h"
#include "lib/dbconn.h"
#include <time.h>               /* For ctime */

//Pin cushion stores pins and timestamps and keeps them
//ordered by timestamp. Works as a circle buffer as new pins get
//added and old ones get deleted.
//We keep track of the oldest pin, the oldest referenced pin and 
//the newest pin.

#define MAX_PINS 1024

typedef struct PT_Entry_t {
        pin_t pin;
        struct timeval timestamp;
        int ref;
} PT_Entry_t;

#define FMT_PT_ENTRY "<" FMT_PIN ", " FMT_TIMEVAL_ABS ">"
#define XVA_PT_ENTRY(e) VA_PIN((e)->pin), XVA_TIMEVAL_ABS((e)->timestamp)

PT_Entry_t table[MAX_PINS];

//points at the newest valid entry in the table
PT_Entry_t *head;
//points right before the oldest valid entry in the table
PT_Entry_t *tail;
//points at the oldest referenced pin
PT_Entry_t *last_ref;

void PT_Init(struct timeval maxStaleness);
PT_Entry_t* PT_FindPin(pin_t pin);
PT_Entry_t* PT_Insert(struct timeval timestamp, pin_t pin);
PT_Entry_t* PT_Find(struct timeval lower_bound, int *num_entries);
PT_Entry_t* PT_GetEntry(int i);
void PT_AddRef(PT_Entry_t *e);
void PT_RemoveRef(PT_Entry_t *e);
void PT_Clean(void);
void PT_Flush(void);
void PT_Print(void);
#define sub(a,b) ((a-b < table) ? (a+MAX_PINS-b) : (a-b))
#define add(a,b) ((a+b < table+MAX_PINS) ? (a+b) : (a-MAX_PINS+b))
#define ptr_sub(a,b) ((a >= b) ? (a-b) : (a+MAX_PINS-b))

#endif
