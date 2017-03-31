// -*- c-file-style: "bsd" -*-

#ifndef _SERVER_PROTO_H_
#define _SERVER_PROTO_H_

static const __attribute__((unused)) char *SERVER_PORT = "16001";

enum Server_Reqs {
        SERVER_REQ_LOOKUP = 'L',
        SERVER_REQ_PUT    = 'P',

        SERVER_REQ_INVAL  = 'I',

        SERVER_REQ_STATS  = 'T',

        SERVER_REQ_SIG    = 'S',
};

enum Server_Reps {
        SERVER_REP_FOUND = 'F',
        SERVER_REP_EMPTY = 'E',

        SERVER_REP_STATS = 'T',
};

#endif // _SERVER_PROTO_H_
