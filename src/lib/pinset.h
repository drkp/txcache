// -*- c-file-style: "bsd" -*-

#ifndef _LIB_PINSET_H_
#define _LIB_PINSET_H_

#include <stdbool.h>

#include "timeval.h"
#include "interval.h"

typedef struct PinStamp_t
{
        pin_t pin;
        struct timeval tv;
} PinStamp_t;

typedef struct PinSet_t
{
        PinStamp_t *pins;
        PinStamp_t *firstPin;
        PinStamp_t onePin;
        int nPins;
        bool andStar;
} PinSet_t;

#define FMT_PINSET "%s"
#define XVA_PINSET(p) Message_DFree(PinSet_Fmt(p))

bool PinSet_Init(PinSet_t *ps, PinStamp_t *pins, int nPins, bool andStar);
void PinSet_Release(PinSet_t *ps);
void PinSet_IntersectWith(PinSet_t *ps, interval_t interval);
bool PinSet_Contains(PinSet_t *ps, pin_t pin);
void PinSet_ReifyStar(PinSet_t *ps, PinStamp_t real);
bool PinSet_JustStar(PinSet_t *ps);
interval_t PinSet_BoundsWithoutStar(PinSet_t *ps);
char *PinSet_Fmt(PinSet_t *ps);

static inline PinStamp_t *
PinSetFirstPin(PinSet_t *ps)
{
        if (ps->nPins == 1)
                return &ps->onePin;
        return ps->firstPin;
}

static inline PinStamp_t *
PinSet_GetPins(PinSet_t *ps, int *nPinsOut)
{
        if (nPinsOut)
                *nPinsOut = ps->nPins;
        return PinSetFirstPin(ps);
}

#endif // _LIB_PINSET_H_
