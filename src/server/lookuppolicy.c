// -*- c-file-style: "bsd" -*-

#include "store.h"
#include "lookuppolicy.h"

static LookupOutcome_t
LatestAcceptable_Lookup(Store_t *store, StoreVSet_t *vset,
                        interval_t interval, pin_t earliestPin,
                        entry_t **result)
{
    // Find latest in cache (less than requested upper bound)
    entry_t *entry = StoreVSetTree_Find(&vset->versions,
                                        interval.upper,
                                        StoreVSetTree_LT);
    
    // Make sure it's within the interval
    if (entry && Interval_Overlaps(entry->interval, interval,
                                   store->lastInvalTime)) {

            *result = entry;
            return LOOKUP_HIT;
    }
    
    // Was too old -- and it was the latest, so no match
    if (entry && (entry->interval.upper >= earliestPin)) {
            return LOOKUP_CONSISTENCY_MISS;
    } else {
            return LOOKUP_STALENESS_MISS;
    }
}

static
struct StoreLookupPolicy_t LookupPolicy_Latest = {
        .lookup = LatestAcceptable_Lookup,
        .name = "latest acceptable version",
        .id = LOOKUP_POLICY_LATEST
};

STORE_REGISTER_LOOKUP_POLICY(LookupPolicy_Latest);


static LookupOutcome_t
ScrewConsistency_Lookup(Store_t *store, StoreVSet_t *vset,
                        interval_t interval, pin_t earliestPin,
                        entry_t **result)
{
        // Find latest in cache
        entry_t *entry = StoreVSetTree_Find(&vset->versions,
                                            interval.upper,
                                            StoreVSetTree_LT);

        // Anything that's still valid or within the client's
        // staleness limit is fair game
        if (entry &&
            (entry->interval.stillValid ||
             (entry->interval.upper >= earliestPin)))
        {
                *result = entry;
                return LOOKUP_HIT;
        }

        // ...but there wasn't anything
        return LOOKUP_STALENESS_MISS;
}

static
struct StoreLookupPolicy_t LookupPolicy_ScrewConsistency = {
        .lookup = ScrewConsistency_Lookup,
        .name = "screw consistency",
        .id = LOOKUP_POLICY_SCREW_CONSISTENCY
};

STORE_REGISTER_LOOKUP_POLICY(LookupPolicy_ScrewConsistency);
