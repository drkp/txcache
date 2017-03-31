// -*- c-file-style: "bsd" -*-

#include "marshal.h"

#include <stdint.h>

#include "lib/iobuf-getput.h"

static bool
MarshalPutArray(IOBuf_t *buf, HashTable *arr)
{
        int count = zend_hash_num_elements(arr);
        IOBuf_PutInt32(buf, count);
        HashPosition pointer;
        zval **data;
        for(zend_hash_internal_pointer_reset_ex(arr, &pointer);
            zend_hash_get_current_data_ex(arr, (void**)&data,
                                          &pointer) == SUCCESS;
            zend_hash_move_forward_ex(arr, &pointer)) {
                char *key;
                unsigned int keyLen;
                unsigned long index;

                if (zend_hash_get_current_key_ex(arr, &key, &keyLen, &index, 0,
                                                 &pointer) == HASH_KEY_IS_STRING) {
                        IOBuf_PutInt32(buf, keyLen);
                        IOBuf_PutBytes(buf, key, keyLen);
                } else {
                        IOBuf_PutInt32(buf, (uint32_t)~0);
                        IOBuf_PutInt64(buf, index);
                }

                if (!Marshal_PutZVal(buf, *data))
                        return false;
        }
        return true;
}

static bool
MarshalGetArray(IOBuf_t *buf, zval *valOut)
{
        bool success = false;
        int count;
        zval *value = NULL;

        if (array_init(valOut) == FAILURE)
                return false;

        if (!IOBuf_TryGetInt32(buf, &count))
                goto end;

        for (int i = 0; i < count; ++i) {
                int keyLen;
                if (!IOBuf_TryGetInt32(buf, &keyLen))
                        goto end;
                ALLOC_INIT_ZVAL(value);
                if (keyLen == (uint32_t)~0) {
                        int64_t i;
                        unsigned long index;
                        if (!IOBuf_TryGetInt64(buf, &i))
                                goto end;
                        index = i;
                        if (!Marshal_GetZVal(buf, value))
                                goto end;
                        if (add_index_zval(valOut, index, value) == FAILURE)
                                goto end;
                } else {
                        const char *key;
                        key = IOBuf_TryGetBytes(buf, keyLen);
                        if (!key)
                                goto end;
                        if (!Marshal_GetZVal(buf, value))
                                goto end;
                        // add_assoc_zval_ex never modified the key
                        // and copies it if necessary
                        if (add_assoc_zval_ex(valOut, (char*)key, keyLen,
                                              value) == FAILURE)
                                goto end;
                }
                value = NULL;
        }
        success = true;

end:
        if (value)
                zval_ptr_dtor(&value);
        if (!success)
                zval_ptr_dtor(&valOut);
        return success;
}

bool
Marshal_PutZVal(IOBuf_t *buf, zval *val)
{
        IOBuf_PutInt32(buf, Z_TYPE_P(val));

        switch (Z_TYPE_P(val)) {
        case IS_NULL:
                break;
        case IS_BOOL:
                IOBuf_PutInt8(buf, Z_LVAL_P(val));
                break;
        case IS_LONG:
                IOBuf_PutInt64(buf, Z_LVAL_P(val));
                break;
        case IS_DOUBLE:
                IOBuf_PutDouble(buf, Z_DVAL_P(val));
                break;
        case IS_STRING:
                IOBuf_PutBuf(buf, Z_STRVAL_P(val), Z_STRLEN_P(val));
                break;
        case IS_RESOURCE:
                Warning("Cannot marshal resource");
                return false;
        case IS_ARRAY:
        {
                HashTable *arr;
                arr = Z_ARRVAL_P(val);
                if (!MarshalPutArray(buf, arr))
                        return false;
                break;
        }
        case IS_OBJECT:
                Warning("Cannot marshal object");
                return false;
        default:
                Warning("Cannot marshal unknown type");
                return false;
        }
        return true;
}

bool
Marshal_GetZVal(IOBuf_t *buf, zval *valOut)
{
        int type;
        if (!IOBuf_TryGetInt32(buf, &type)) {
                Warning("Failed to unmarshal type code");
                return false;
        }

        bool success = true;
        switch (type) {
        case IS_NULL:
                break;
        case IS_BOOL:
        {
                int8_t v = 0;
                success = IOBuf_TryGetInt8(buf, &v);
                valOut->value.lval = v;
                break;
        }
        case IS_LONG:
        {
                int64_t v = 0;
                success = IOBuf_TryGetInt64(buf, &v);
                valOut->value.lval = v;
                break;
        }
        case IS_DOUBLE:
                success = IOBuf_TryGetDouble(buf, &valOut->value.dval);
                break;
        case IS_STRING:
        {
                const char *v;
                size_t len;
                v = IOBuf_TryGetBuf(buf, &len);
                if (v) {
                        ZVAL_STRINGL(valOut, (char*)v, len, 1);
                } else {
                        success = false;
                }
                break;
        }
        case IS_ARRAY:
                success = MarshalGetArray(buf, valOut);
                break;
        default:
                Warning("Unexpected type code");
                success = false;
                break;
        }
        if (success)
                valOut->type = type;
        else
                ZVAL_NULL(valOut);
        return success;
}
