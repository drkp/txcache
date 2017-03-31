// -*- c-file-style: "bsd" -*-

#ifndef _PINCUSHION_PROXY_H_
#define _PINCUSHION_PROXY_H_

#include <sys/time.h>

#include "proto.h"
#include "lib/rpc.h"
#include "lib/interval.h"
#include "lib/pinset.h"

/**
 * Request the set of pins whose timestamps occur at most freshness
 * ago and reference the returned set of pins.  If any pins are
 * returned, this function will return a dynamically allocated buffer
 * of timestamped pin numbers and set *numPinsOut.  The caller is
 * responsible for freeing the returned buffer.  If no pins satisfy
 * the freshness requirement, this causes the pin cushion to reference
 * the latest pin, sets *numPinsOut to 0 and returns NULL.
 */
PinStamp_t *PincushionProxy_Request(host_t *h, struct timeval freshness,
                                    uint32_t *numPinsOut);
/**
 * Add a timestamped pin to the pin cushion.  This does not wait for a
 * response, so there is no guarantee the operation completed
 * successfully.
 */
void PincushionProxy_Insert(host_t *h, PinStamp_t pinstamp);
/**
 * Release all pins this host currently references in the pin cushion.
 */
void PincushionProxy_Release(host_t *h);

#endif // _PINCUSHION_PROXY_H_
