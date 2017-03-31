#ifndef _PHPCLIENT_MARSHAL_H_
#define _PHPCLIENT_MARSHAL_H_

#include <php.h>

#include "lib/iobuf.h"

bool Marshal_PutZVal(IOBuf_t *buf, zval *val);
bool Marshal_GetZVal(IOBuf_t *buf, zval *valOut);

#endif // _PHPCLIENT_MARSHAL_H_
