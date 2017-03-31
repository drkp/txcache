// -*- c-file-style: "bsd" -*-

#include "reactor.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include "lib/message.h"

%instance DblListImpl ReactorEntryList(struct Reactor_Entry_t, link);

static bool reactorInitialized;
static Reactor_t reactor;

static void ReactorLoop(bool untilStable);

static void
Reactor_InitOnce(void)
{
        if (reactorInitialized)
                return;

        FD_ZERO(&reactor.recvSet);
        FD_ZERO(&reactor.sendSet);
        ReactorEntryList_Init(&reactor.entries);
        reactor.eagerMode = false;
        reactorInitialized = true;
}

Reactor_Entry_t*
Reactor_Add(int fd, Reactor_Handler_t onReadable,
            Reactor_Handler_t onWritable, void *data)
{
        Reactor_InitOnce();

        Reactor_Entry_t *entry = malloc(sizeof *entry);
        if (!entry) {
                Warning("Failed to allocate reactor entry");
                return NULL;
        }

        memset(entry, 0, sizeof(*entry));

        if (fd < 0)
                Panic("Invalid fd %d", fd);
        entry->fd = fd;
        entry->onReadable = onReadable;
        entry->onWritable = onWritable;
        entry->data = data;
        entry->deferFree = false;
        entry->reactor = &reactor;
        ReactorEntryList_PushFront(&reactor.entries, entry);
        FD_SET(entry->fd, &entry->reactor->recvSet);
        return entry;
}

void
Reactor_EagerMode(void)
{
        Reactor_InitOnce();
        reactor.eagerMode = true;
}

void
Reactor_LoopIfEager(void)
{
        if (reactor.eagerMode == true)
                ReactorLoop(true);
}

void
Reactor_MarkWritable(Reactor_Entry_t *entry)
{
        if (entry->fd == -1)
                Panic("Cannot mark closed reactor entry writable");
        FD_SET(entry->fd, &entry->reactor->sendSet);

        if (entry->reactor->eagerMode) {
                // The top-level loop will take care of everything.
                // Temporarily disable eager mode so we don't wind up
                // with deep call stacks.  (Would *not* doing this
                // result in useful debugging back traces?)
                entry->reactor->eagerMode = false;
                Debug("Entering eager reactor loop");
                ReactorLoop(true);
                Debug("Exited eager reactor loop");
                entry->reactor->eagerMode = true;
        }
}

static void
Reactor_Free(Reactor_Entry_t *entry)
{
        ReactorEntryList_Unlink(&entry->reactor->entries, entry);
        free(entry);
}

void
Reactor_Close(Reactor_Entry_t *entry)
{
        if (entry->fd == -1)
                Panic("Attempt to close reactor entry twice");
        FD_CLR(entry->fd, &entry->reactor->recvSet);
        FD_CLR(entry->fd, &entry->reactor->sendSet);
        close(entry->fd);
        entry->fd = -1;
        if (!entry->deferFree)
                Reactor_Free(entry);
}

static void
ReactorLoop(bool untilStable)
{
        Reactor_t *r = &reactor;

        while (1) {
                struct timeval tvZero = {0, 0};
                fd_set recvReadySet = r->recvSet, sendReadySet = r->sendSet;

                int count = select(FD_SETSIZE, &recvReadySet, &sendReadySet,
                                   NULL, untilStable ? &tvZero : NULL);

                if (untilStable && count == 0) {
                        return;
                } else if (count < 0) {
                        if (errno == EINTR)
                                continue;
                        PWarning("Reactor select loop failed");
                        continue;
                }

                Reactor_Entry_t *entry, *next;
                for (entry = r->entries.head; entry;) {
                        if (entry->fd == -1) {
                                // This can happen if a handler closes
                                // a reactor entry and then enters a
                                // recursive reactor loop
                                entry = entry->link.next;
                                continue;
                        }
                        if (entry->reactor != r)
                                Panic("Mismatched entry reactor");
                        entry->deferFree = true;
                        if (FD_ISSET(entry->fd, &recvReadySet)) {
                                entry->onReadable(entry, entry->fd, entry->data);
                                if (entry->fd == -1) {
                                        next = entry->link.next;
                                        Reactor_Free(entry);
                                        entry = next;
                                        continue;
                                }
                        }
                        if (FD_ISSET(entry->fd, &sendReadySet)) {
                                FD_CLR(entry->fd, &entry->reactor->sendSet);
                                entry->onWritable(entry, entry->fd, entry->data);
                                if (entry->fd == -1) {
                                        next = entry->link.next;
                                        Reactor_Free(entry);
                                        entry = next;
                                        continue;
                                }
                        }
                        entry->deferFree = false;
                        entry = entry->link.next;
                }
        }
}

void
Reactor_Loop(void)
{
        ReactorLoop(false);
}
