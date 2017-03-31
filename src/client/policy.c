#include "client.h"

static pin_t
ChooseLargestBoundedPin(PinSet_t *ps)
{
        if (PinSet_JustStar(ps))
                return PIN_INF;
        return PinSet_BoundsWithoutStar(ps).upper - 1;
}

static
struct ClientPolicyChoosePin_t Policy_LargestBoundedPin = {
        .fn = ChooseLargestBoundedPin,
        .name = "largest bounded pin",
};

CLIENT_REGISTER_POLICY(Policy_LargestBoundedPin);

static pin_t
ChooseLargestPin(PinSet_t *ps)
{
        if (ps->andStar)
                return PIN_INF;
        return PinSet_BoundsWithoutStar(ps).upper - 1;
}

static
struct ClientPolicyChoosePin_t Policy_LargestPin = {
        .fn = ChooseLargestPin,
        .name = "largest pin",
};

CLIENT_REGISTER_POLICY(Policy_LargestPin);

static inline pin_t
ChooseVariety(PinSet_t *ps, int secsMin, int secsMax)
{
        if (PinSet_JustStar(ps))
                return PIN_INF;

        if (ps->andStar) {
                int nPins;
                PinStamp_t *pins = PinSet_GetPins(ps, &nPins);

                // Choose a time in [secsMin, secsMax].  Note that
                // this is safe in a world without invalidations,
                // since we only do this if * is in the pin set and *
                // can only be in the pin set if this transaction
                // hasn't done anything yet.
                int secs;
                if (secsMax == secsMin)
                        secs = secsMin;
                else
                        secs = (rand() % (secsMax - secsMin + 1)) + secsMin;

                // How old is the latest pin?
                struct timeval bound;
                if (gettimeofday(&bound, NULL) < 0)
                        PPanic("Failed to get time of day");
                bound.tv_sec -= secs;
                PinStamp_t *latest = &pins[nPins-1];
                if (timeval_lessthan(latest->tv, bound)) {
                        // Too old.  Choose * to create variety
                        Debug("Latest pin is > %d secs old (" FMT_TIMEVAL_ABS
                              " < " FMT_TIMEVAL_ABS ").  Choosing *",
                              secs, XVA_TIMEVAL_ABS(latest->tv),
                              XVA_TIMEVAL_ABS(bound));
                        return PIN_INF;
                }
        }
        return PIN_INVALID;
}

static pin_t
ChooseMiddlePinWithVariety5(PinSet_t *ps)
{
        pin_t variety = ChooseVariety(ps, 5, 5);
        if (variety != PIN_INVALID)
                return variety;

        // Choose a pin in the middle
        int nPins;
        PinStamp_t *pins = PinSet_GetPins(ps, &nPins);
        return pins[nPins / 2].pin;
}

static
struct ClientPolicyChoosePin_t Policy_MiddlePinWithVariety5 = {
        .fn = ChooseMiddlePinWithVariety5,
        .name = "middle pin with variety (5 secs)",
};

CLIENT_REGISTER_POLICY(Policy_MiddlePinWithVariety5);

static pin_t
ChooseLatestBoundedPinWithVariety5(PinSet_t *ps)
{
        pin_t variety = ChooseVariety(ps, 5, 5);
        if (variety != PIN_INVALID)
                return variety;

        // Choose the latest bounded pin
        int nPins;
        PinStamp_t *pins = PinSet_GetPins(ps, &nPins);
        return pins[nPins-1].pin;
}

static
struct ClientPolicyChoosePin_t Policy_LatestBoundedPinWithVariety5 = {
        .fn = ChooseLatestBoundedPinWithVariety5,
        .name = "latest bounded pin with variety (5 secs)",
};

CLIENT_REGISTER_POLICY(Policy_LatestBoundedPinWithVariety5);

static pin_t
ChooseLatestBoundedPinWithVariety5To20(PinSet_t *ps)
{
        // Jitter between 5 and 20 seconds.  This makes it less likely
        // for clients to race and create multiple pins every 5
        // seconds.
        pin_t variety = ChooseVariety(ps, 5, 20);
        if (variety != PIN_INVALID)
                return variety;

        // Choose the latest bounded pin
        int nPins;
        PinStamp_t *pins = PinSet_GetPins(ps, &nPins);
        return pins[nPins-1].pin;
}

static
struct ClientPolicyChoosePin_t Policy_LatestBoundedPinWithVariety5To20 = {
        .fn = ChooseLatestBoundedPinWithVariety5To20,
        .name = "latest bounded pin with variety (5 to 20 secs)",
};

CLIENT_REGISTER_POLICY(Policy_LatestBoundedPinWithVariety5To20);
