// -*- c-file-style: "bsd" -*-

#ifndef _LIB_INTERVAL_H_
#define _LIB_INTERVAL_H_

#include <stdbool.h>
#include <stdint.h>

#include "lib/message.h"

#ifndef MAX
#define MAX(x,y) (((x) < (y)) ? (y) : (x))
#endif

typedef uint32_t pin_t;

#define PIN_INVALID ((pin_t)0)
#define PIN_NEG_INF ((pin_t)1)
#define PIN_MIN     ((pin_t)2)
#define PIN_INF     ((pin_t)~0)

#define PIN_SPECIAL(p)                                                  \
        ((p) == PIN_INVALID || (p) == PIN_NEG_INF || (p) == PIN_INF)
#define FMT_PIN "%s%.*u"
#define VA_PIN(p)                               \
        (p) == PIN_INVALID ? "INV" :            \
                (p) == PIN_NEG_INF ? "-inf" :   \
                (p) == PIN_INF ? "inf" : "",    \
                PIN_SPECIAL(p) ? 0 : 1,         \
                PIN_SPECIAL(p) ? 0 : (p)

typedef struct interval_t
{
        pin_t lower;
        pin_t upper;
        bool stillValid;
} interval_t;

#define FMT_INTERVAL "[" FMT_PIN "," FMT_PIN "%s)"
#define VA_INTERVAL(i) VA_PIN((i).lower), VA_PIN((i).upper), \
                      ((i).stillValid ? "+" : "")

static inline void
Interval_AssertValid(interval_t a)
{
        Assert(a.lower != PIN_INVALID);
        Assert(a.upper != PIN_INVALID);
        Assert(a.lower != PIN_INF);
        Assert(a.upper != PIN_NEG_INF);
        Assert(a.lower == PIN_NEG_INF || a.upper == PIN_INF || a.lower < a.upper);
}

static inline bool
Interval_Overlaps(interval_t a, interval_t b, pin_t now)
{
        Interval_AssertValid(a);
        Interval_AssertValid(b);
        if (a.lower == PIN_NEG_INF || a.lower < b.lower)
        {
                pin_t aUpper;
                if (a.stillValid)
                        aUpper = MAX(a.upper, now);
                else
                        aUpper = a.upper;
                return ((a.upper == PIN_INF) ||
                        (aUpper > b.lower));
        }
        else
        {
                pin_t bUpper;
                if (b.stillValid)
                        bUpper = MAX(b.upper, now);
                else
                        bUpper = b.upper;

                return ((b.upper == PIN_INF) ||
                        (bUpper > a.lower));
        }
}

static inline interval_t
Interval_Intersect(interval_t a, interval_t b, pin_t now)
{
        Assert(Interval_Overlaps(a, b, now));
        if (a.stillValid)
                a.upper = MAX(a.upper, now);
        if (b.stillValid)
                b.upper = MAX(b.upper, now);
        
        interval_t res = a;
        res.stillValid = a.stillValid && b.stillValid;
        if (a.lower < b.lower)
                res.lower = b.lower;
        if (a.upper > b.upper)
                res.upper = b.upper;
        return res;
}

#endif // _LIB_INTERVAL_H_
