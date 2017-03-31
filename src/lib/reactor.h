// -*- c-file-style: "bsd" -*-

#ifndef _LIB_REACTOR_H_
#define _LIB_REACTOR_H_

#include <stdbool.h>
#include <sys/select.h>

#include "lib/tmpl/dbllist.h"

struct Reactor_Entry_t;
typedef void (*Reactor_Handler_t)(struct Reactor_Entry_t *, int, void *);

%instance DblList ReactorEntryList(struct Reactor_Entry_t, link);

typedef struct Reactor_Entry_t
{
        int fd;
        Reactor_Handler_t onReadable;
        Reactor_Handler_t onWritable;
        void *data;
        bool deferFree;
        struct Reactor_t *reactor;
        struct ReactorEntryList_Link_t link;
} Reactor_Entry_t;

typedef struct Reactor_t
{
        fd_set recvSet;
        fd_set sendSet;
        ReactorEntryList_t entries;
        bool eagerMode;
} Reactor_t;

Reactor_Entry_t *Reactor_Add(int fd, Reactor_Handler_t onReadable,
                             Reactor_Handler_t onWritable, void *data);
void Reactor_EagerMode(void);
void Reactor_LoopIfEager(void);
void Reactor_MarkWritable(Reactor_Entry_t *entry);
void Reactor_Close(Reactor_Entry_t *entry);
void Reactor_Loop(void);

#endif // _LIB_REACTOR_H_
