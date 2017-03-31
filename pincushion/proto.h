// -*- c-file-style: "bsd" -*-

#ifndef _PINCUSHION_PROTO_H_
#define _PINCUSHION_PROTO_H_

static const __attribute__((unused)) char *PINCUSHION_PORT = "16000";

enum Pincushion_Reqs {
        PINCUSHION_REQ_REQUEST = 'R',
        PINCUSHION_REQ_INSERT  = 'I',
        PINCUSHION_REQ_RELEASE = 'D',
	PINCUSHION_REQ_SIG     = 'S'
};

enum Pincushion_Reps {
        PINCUSHION_REP_FOUND = 'F',
        PINCUSHION_REP_EMPTY = 'E',
};

#endif // _PINCUSHION_PROTO_H_
