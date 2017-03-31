// -*- c-file-style: "bsd" -*-

#ifndef PARANOID
#define PARANOID 0
#endif

#include "pinset.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static PinStamp_t PINSTAMP_INVALID = {
        .pin = PIN_INVALID,
        .tv = {0, 0},
};

static inline void
PinSetAssertValid(PinSet_t *ps)
{
        if (PARANOID) {
                if (ps->nPins == 0) {
                        Assert(ps->pins == NULL);
                        Assert(ps->firstPin == NULL);
                        Assert(ps->onePin.pin == PIN_INVALID);
                } else if (ps->nPins == 1) {
                        Assert(ps->pins == NULL);
                        Assert(ps->firstPin == NULL);
                        Assert(ps->onePin.pin != PIN_INVALID);
                } else {
                        Assert(ps->pins);
                        Assert(ps->firstPin);
                        Assert(ps->firstPin >= ps->pins);
                        Assert(ps->onePin.pin == PIN_INVALID);
                        for (int i = 0; i < ps->nPins - 1; ++i) {
                                Assert(ps->pins[i].pin != PIN_INVALID);
                                if (ps->pins[i].pin > ps->pins[i+1].pin) {
                                        Panic("Pin set " FMT_PINSET
                                              " out of order",
                                              XVA_PINSET(ps));
                                }
                        }
                        Assert(ps->pins[ps->nPins - 1].pin != PIN_INVALID);
                }
        }
}

static inline void
PinSetUpdate(PinSet_t *ps, PinStamp_t *firstPin, int nPins, bool andStar)
{
        Assert(nPins >= 0);
        if (nPins <= 1) {
                ps->onePin = (nPins == 0 ? PINSTAMP_INVALID : *firstPin);
                if (ps->pins)
                        free(ps->pins);
                ps->pins = ps->firstPin = NULL;
        } else {
                ps->firstPin = firstPin;
                ps->onePin = PINSTAMP_INVALID;
        }
        ps->nPins = nPins;
        ps->andStar = andStar;
        PinSetAssertValid(ps);
}

bool
PinSet_Init(PinSet_t *ps, PinStamp_t *pins, int nPins, bool andStar)
{
        Assert(nPins > 0 || andStar);
        ps->pins = pins;
        PinSetUpdate(ps, pins, nPins, andStar);
        return true;
}

void
PinSet_Release(PinSet_t *ps)
{
        PinSetUpdate(ps, NULL, 0, false);
}

void
PinSet_IntersectWith(PinSet_t *ps, interval_t interval)
{
        // XXX We could use binary search
        PinStamp_t *first = PinSetFirstPin(ps);
        PinStamp_t *last = first + ps->nPins - 1;

        if (first) {
                while (first <= last && last->pin >= interval.upper) {
                        --last;
                }
                while (first <= last && first->pin < interval.lower) {
                        ++first;
                }
        }

        Assert(ps->andStar || last - first + 1 > 0);
        PinSetUpdate(ps, first, last - first + 1, ps->andStar && interval.upper == PIN_INF);
}

bool
PinSet_Contains(PinSet_t *ps, pin_t pin)
{
        if (pin == PIN_INF)
                return ps->andStar;
        if (pin == PIN_INVALID)
                return false;

        // XXX We could use binary search
        PinStamp_t *cur = PinSetFirstPin(ps);
        PinStamp_t *end = cur + ps->nPins;
        while (cur < end && cur->pin < pin)
                ++cur;
        return (cur < end && cur->pin == pin);
}

void
PinSet_ReifyStar(PinSet_t *ps, PinStamp_t real)
{
        Assert(ps->andStar);
        PinSetUpdate(ps, &real, 1, false);
}

bool
PinSet_JustStar(PinSet_t *ps)
{
        return ps->nPins == 0 && ps->andStar;
}

interval_t
PinSet_BoundsWithoutStar(PinSet_t *ps)
{
        Assert(ps->nPins > 0);

        interval_t res = {
                .lower = PinSetFirstPin(ps)->pin,
                .upper = (PinSetFirstPin(ps) + ps->nPins - 1)->pin + 1,
                .stillValid = false,
        };
        return res;
}

char *
PinSet_Fmt(PinSet_t *ps)
{
        char *buf = malloc(128);
        if (!buf)
                return NULL;
        char *pos = buf;
        *(pos++) = '{';

        PinStamp_t *p = PinSetFirstPin(ps);
        int i;
        int lim = ps->nPins == 4 ? 4 : 3;
        for (i = 0; i < ps->nPins && i < lim; ++i) {
                sprintf(pos, "%s" FMT_PIN, i == 0 ? "" : ", ", VA_PIN(p[i].pin));
                pos += strlen(pos);
        }
        if (i < ps->nPins) {
                sprintf(pos, ", ..., " FMT_PIN, VA_PIN(p[ps->nPins - 1].pin));
                pos += strlen(pos);
        }
        if (ps->andStar) {
                sprintf(pos, "%s*", i == 0 ? "" : ", ");
                pos += strlen(pos);
        }
        strcpy(pos, "}");
        return buf;
}
