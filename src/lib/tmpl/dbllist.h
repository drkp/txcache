// -*- c-file-style: "bsd" -*-

#ifndef _LIB_TMPL_DBLLIST_H_
#define _LIB_TMPL_DBLLIST_H_

// Check that the interface is being used correctly
#ifndef PARANOID
#define PARANOID 0
#endif
// Check that the list implementation is working
#ifndef UBERPARANOID
#define UBERPARANOID 0
#endif

#if PARANOID || UBERPARANOID
#include <assert.h>
#endif

%template DblList LIST(CONTAINER_t, FIELD);

typedef struct LIST_t
{
        CONTAINER_t *head;
        CONTAINER_t *tail;
} LIST_t;

typedef struct LIST_Link_t
{
        CONTAINER_t *prev;
        CONTAINER_t *next;
#if PARANOID
        LIST_t *list;
#endif
} LIST_Link_t;

void LIST_Init(LIST_t *l);
void LIST_PushFront(LIST_t *l, CONTAINER_t *v);
void LIST_PushBack(LIST_t *l, CONTAINER_t *v);
void LIST_Unlink(LIST_t *l, CONTAINER_t *v);

%endtemplate;

%template DblListImpl LIST(CONTAINER_t, FIELD);

#if UBERPARANOID
void
LIST_CheckRep(LIST_t *l)
{
        int i = 0;
        if (l->head)
                assert(l->head->FIELD.prev == 0);
        if (l->tail)
                assert(l->tail->FIELD.next == 0);
        for (CONTAINER_t *v = l->head; v; v = v->FIELD.next, ++i) {
                assert(v->FIELD.list == l);
                if (v->FIELD.next)
                        assert(v->FIELD.next->FIELD.prev == v);
                else
                        assert(l->tail == v);
                if (v->FIELD.prev)
                        assert(v->FIELD.prev->FIELD.next == v);
                else
                        assert(l->head == v);
        }
}
#endif

void
LIST_Init(LIST_t *l)
{
        l->head = l->tail = 0;
}

void
LIST_PushFront(LIST_t *l, CONTAINER_t *v)
{
#if PARANOID
        v->FIELD.list = l;
#endif
#if UBERPARANOID
        assert((l->head && l->tail) || (!l->head && !l->tail));
#endif
        v->FIELD.prev = 0;
        v->FIELD.next = l->head;
        if (l->head)
                l->head->FIELD.prev = v;
        l->head = v;
        if (!l->tail)
                l->tail = v;
#if UBERPARANOID
        assert(l->head);
        assert(l->tail);
        LIST_CheckRep(l);
#endif
}

void
LIST_PushBack(LIST_t *l, CONTAINER_t *v)
{
#if PARANOID
        v->FIELD.list = l;
#endif
#if UBERPARANOID
        assert((l->head && l->tail) || (!l->head && !l->tail));
#endif
        v->FIELD.prev = l->tail;
        v->FIELD.next = 0;
        if (l->tail)
                l->tail->FIELD.next = v;
        l->tail = v;
        if (!l->head)
                l->head = v;
#if UBERPARANOID
        assert(l->head);
        assert(l->tail);
        LIST_CheckRep(l);
#endif
}

void
LIST_Unlink(LIST_t *l, CONTAINER_t *v)
{
#if PARANOID
        assert(v->FIELD.list != 0);
        assert(v->FIELD.list == l);
        v->FIELD.list = 0;
#endif
#if UBERPARANOID
        assert((l->head && l->tail) || (!l->head && !l->tail));
        assert(l->head);
        assert(l->tail);
#endif
        if (v->FIELD.next)
                v->FIELD.next->FIELD.prev = v->FIELD.prev;
        else
                l->tail = v->FIELD.prev;
        if (v->FIELD.prev)
                v->FIELD.prev->FIELD.next = v->FIELD.next;
        else
                l->head = v->FIELD.next;
#if UBERPARANOID
        assert((l->head && l->tail) || (!l->head && !l->tail));
        LIST_CheckRep(l);
#endif
}

%endtemplate;

#endif // _LIB_TMPL_DBLLIST_H_
