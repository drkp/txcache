#include "test.h"

int
Test_Main(Suite *suite)
{
        int nFailed;
        Suite *s = suite;
        SRunner *sr = srunner_create(s);
        srunner_run_all(sr, CK_NORMAL);
        nFailed = srunner_ntests_failed(sr);
        srunner_free(sr);
        return (nFailed == 0) ? 0 : 1;
}
