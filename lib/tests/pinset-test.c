#include "lib/test.h"

#include <stdarg.h>
#include <stdlib.h>

#include "lib/pinset.h"

#define PSMake(ps, star, ...) _PSMake(ps, star, ## __VA_ARGS__, PIN_INVALID)

static PinSet_t *
_PSMake(PinSet_t *ps, bool star, ...)
{
        PinSet_Release(ps);

        int count;
        va_list args;
        va_start(args, star);
        for (count = 0; ; ++count) {
                pin_t want = va_arg(args, pin_t);
                if (want == PIN_INVALID)
                        break;
        }
        va_end(args);

        PinStamp_t *pins = NULL;
        if (count)
                fail_unless((pins = malloc(count * sizeof *pins)) != NULL,
                            "Failed to allocate pin array");

        va_start(args, star);
        for (int i = 0; i < count; ++i) {
                pins[i].pin = va_arg(args, pin_t);
                pins[i].tv.tv_sec = i;
                pins[i].tv.tv_usec = 0;
        }
        va_end(args);

        fail_unless(PinSet_Init(ps, pins, count, star),
                    "Failed to initialized pin set");
        return ps;
}

#define PSCheck(ps, star, ...)                                  \
        do {                                                    \
                PinSet_t _psCheck = {};                         \
                PSMake(&_psCheck, star, ## __VA_ARGS__);        \
                PSCheckSame(&_psCheck, ps);                     \
                PinSet_Release(&_psCheck);                      \
        } while (0)

static void
PSCheckSame(PinSet_t *want, PinSet_t *got)
{
        fail_unless(want->andStar == got->andStar,
                    "Expected %sstar, got %sstar",
                    want->andStar ? "": " no", got->andStar ? "" : " no");

        PinStamp_t *pwant = PinSetFirstPin(want);
        PinStamp_t *pgot = PinSetFirstPin(got);
        for (int i = 0; i < want->nPins; ++i, ++pwant, ++pgot) {
                if (i == got->nPins) {
                        fail("Expected pin "FMT_PIN" not found",
                             VA_PIN(pwant->pin));
                }
                if (pgot->pin != pwant->pin) {
                        fail("Expected pin "FMT_PIN", got "FMT_PIN,
                             VA_PIN(pwant->pin), VA_PIN(pgot->pin));
                }
        }
        if (want->nPins < got->nPins) {
                fail("%d unexpected pins", got->nPins - want->nPins);
        }
}

static interval_t
interval(pin_t lower, pin_t upper)
{
        interval_t res = {.lower = lower, .upper = upper,
                          .stillValid = false};
        return res;
}

static void
IVCheck(interval_t want, interval_t got)
{
        fail_unless(want.lower == got.lower && want.upper == got.upper &&
                    want.stillValid == got.stillValid,
                    "Expected " FMT_INTERVAL ", got " FMT_INTERVAL,
                    VA_INTERVAL(want), VA_INTERVAL(got));
}

START_TEST(intersect_with)
{
        PinSet_t ps = {};
        interval_t iv;

        // [unbounded, unbounded)
        iv = interval(PIN_NEG_INF, PIN_INF);
        PinSet_IntersectWith(PSMake(&ps, true), iv);
        PSCheck(&ps, true);
        PinSet_IntersectWith(PSMake(&ps, true, 10), iv);
        PSCheck(&ps, true, 10);
        PinSet_IntersectWith(PSMake(&ps, true, 10, 11), iv);
        PSCheck(&ps, true, 10, 11);
        PinSet_IntersectWith(PSMake(&ps, false, 10), iv);
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11), iv);
        PSCheck(&ps, false, 10, 11);

        // [bounded, unbounded)
        iv = interval(PIN_MIN, PIN_INF);
        PinSet_IntersectWith(PSMake(&ps, true), iv);
        PSCheck(&ps, true);
        PinSet_IntersectWith(PSMake(&ps, true, 10), iv);
        PSCheck(&ps, true, 10);
        PinSet_IntersectWith(PSMake(&ps, true, 10, 11), iv);
        PSCheck(&ps, true, 10, 11);
        PinSet_IntersectWith(PSMake(&ps, false, 10), iv);
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11), iv);
        PSCheck(&ps, false, 10, 11);

        // [unbounded, bounded)
        iv = interval(PIN_NEG_INF, 99);
        PinSet_IntersectWith(PSMake(&ps, true, 10), iv);
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, true, 10, 11), iv);
        PSCheck(&ps, false, 10, 11);
        PinSet_IntersectWith(PSMake(&ps, false, 10), iv);
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11), iv);
        PSCheck(&ps, false, 10, 11);

        // [bounded, bounded)
        iv = interval(PIN_MIN, 99);
        PinSet_IntersectWith(PSMake(&ps, true, 10), iv);
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, true, 10, 11), iv);
        PSCheck(&ps, false, 10, 11);
        PinSet_IntersectWith(PSMake(&ps, false, 10), iv);
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11), iv);
        PSCheck(&ps, false, 10, 11);

        // Interval edge cases
        PinSet_IntersectWith(PSMake(&ps, false, 10, 15), interval(PIN_MIN, 15));
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 15), interval(PIN_MIN, 16));
        PSCheck(&ps, false, 10, 15);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 15), interval(10, 16));
        PSCheck(&ps, false, 10, 15);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 15), interval(11, 16));
        PSCheck(&ps, false, 15);

        // More than one
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11, 12, 13), interval(10, 12));
        PSCheck(&ps, false, 10, 11);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11, 12, 13), interval(10, 11));
        PSCheck(&ps, false, 10);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11, 12, 13), interval(12, 14));
        PSCheck(&ps, false, 12, 13);
        PinSet_IntersectWith(PSMake(&ps, false, 10, 11, 12, 13), interval(13, 14));
        PSCheck(&ps, false, 13);

        PinSet_Release(&ps);
}
END_TEST;

START_TEST(bounds_without_star)
{
        PinSet_t ps = {};

        IVCheck(interval(42, 43),
                PinSet_BoundsWithoutStar(PSMake(&ps, false, 42)));
        IVCheck(interval(42, 44),
                PinSet_BoundsWithoutStar(PSMake(&ps, false, 42, 43)));
        IVCheck(interval(42, 50),
                PinSet_BoundsWithoutStar(PSMake(&ps, false, 42, 49)));
        IVCheck(interval(42, 50),
                PinSet_BoundsWithoutStar(PSMake(&ps, false, 42, 46, 49)));
        IVCheck(interval(PIN_NEG_INF, 50),
                PinSet_BoundsWithoutStar(PSMake(&ps, false, PIN_NEG_INF, 42, 49)));

        PinSet_Release(&ps);
}
END_TEST;

int
main(void)
{
        Suite *s = suite_create("pinset");

        TCase *tc = tcase_create("Core");
        tcase_add_test(tc, intersect_with);
        tcase_add_test(tc, bounds_without_star);
        suite_add_tcase(s, tc);

        return Test_Main(s);
}
