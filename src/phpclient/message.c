// -*- c-file-style: "bsd" -*-

#include <php.h>

#include "lib/message.h"

void
Message_VA(enum Message_Type type,
              const char *fname, int line, const char *func,
              const char *fmt, va_list args)
{
        static const int descs[MSG_NUM_TYPES + 1] = {
                [MSG_PANIC]     = E_ERROR,
                [MSG_WARNING]   = E_WARNING,
                [MSG_NOTICE]    = E_NOTICE,
                [MSG_DEBUG]     = E_NOTICE,
                [MSG_NUM_TYPES] = E_NOTICE,
        };

        if (1 && PG(html_errors)) {
                static FILE *fp = NULL;
                if (!fp) {
                        if (access("/tmp/clog/", R_OK) < 0) {
                                mkdir("/tmp/clog", 0777);
                                chmod("/tmp/clog", 0777);
                        }
                        char buf[128];
                        sprintf(buf, "/tmp/clog/%d", getpid());
                        fp = fopen(buf, "w+");
                        assert(fp);
                }
                _Message_VA(type, fp, fname, line, func, fmt, args);
                return;
        }

        if (!PG(html_errors)) {
                _Message_VA(type, stderr, fname, line, func, fmt, args);
                return;
        }

        int nDesc = type & (~MSG_PERROR);
        if (nDesc > MSG_NUM_TYPES)
                nDesc = MSG_NUM_TYPES;

        php_verror(NULL, "", descs[nDesc], fmt, args TSRMLS_CC);
}
