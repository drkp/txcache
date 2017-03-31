#include "lib/test.h"

#include "lib/tmpl/rbtree.h"

%instance RBTree RB(int, int);

int valueCount;

static void
RB_InitValue(int *val, int key)
{
        *val = key;
        ++valueCount;
}

static void
RB_ReleaseValue(int *val)
{
        --valueCount;
}

static int
RB_CompareValue(int *val, int key)
{
        return *val - key;
}

%instance RBTreeImpl RB(int, int, RB_InitValue, RB_ReleaseValue, RB_CompareValue);

// Based on jsw_rb_assert
int RB_Check (struct RB_node_t *root)
{
        int lh, rh;

        if (root == NULL)
                return 1;
        else {
                struct RB_node_t *ln = root->link[0];
                struct RB_node_t *rn = root->link[1];

                /* Consecutive red links */
                if (RB_IsRed(root)) {
                        if (RB_IsRed(ln) || RB_IsRed(rn)) {
                                fail("Red violation");
                                return 0;
                        }
                }

                lh = RB_Check(ln);
                rh = RB_Check(rn);

                /* Invalid binary search tree */
                if ((ln != NULL && ln->data >= root->data)
                    || (rn != NULL && rn->data <= root->data))
                {
                        fail("Binary tree violation");
                        return 0;
                }

                /* Black height mismatch */
                if (lh != 0 && rh != 0 && lh != rh) {
                        fail("Black violation");
                        return 0;
                }

                /* Only count black links */
                if (lh != 0 && rh != 0)
                        return RB_IsRed(root) ? lh : lh + 1;
                else
                        return 0;

                if (root->prev != NULL)
                    fail_unless(root->prev->next == root);
                if (root->next != NULL)
                    fail_unless(root->next->prev == root);
        }
}

#define ARRAYSIZE(arr) (sizeof(arr) / sizeof(arr[0]))

int dense[] = {0, 11, 10, 5, 9, 19, 8, 18, 2, 12,
               4, 1, 7, 17, 13, 14, 3, 6, 15, 16};

int sparse[] = {21, 47, 39, 35, 20, 19, 28, 30, 29, 26,
                49, 48, 46, 37, 2, 45, 9, 25, 34, 24};

START_TEST(init)
{
        RB_t tree;
        fail_unless(RB_Init(&tree), NULL);
        fail_unless(valueCount == 0, NULL);
        RB_Release(&tree);
        fail_unless(valueCount == 0, NULL);
        RB_Release(&tree);
}
END_TEST;

START_TEST(insert)
{
        RB_t tree;
        fail_unless(RB_Init(&tree), NULL);
        for (int i = 0; i < ARRAYSIZE(dense); ++i) {
                fail_unless(RB_Insert(&tree, dense[i]) != NULL, NULL);
                RB_Check(tree.root);
                fail_unless(valueCount == i + 1, NULL);
                fail_unless(RB_Size(&tree) == i + 1, NULL);
        }
        RB_Release(&tree);
}
END_TEST;

int array_find(int *arr, int len, int val, RB_Relation rel)
{
        int best = -1;
        for (int i = 0; i < len; ++i) {
                bool valid = false;
                switch (rel) {
                case RB_EQ:
                        valid = (arr[i] == val);
                        break;
                case RB_LT:
                        valid = (arr[i] < val);
                        break;
                case RB_GE:
                        valid = (arr[i] >= val);
                        break;
                }
                if (valid && (best == -1 || abs(arr[i] - val) < abs(best - val))) {
                        best = arr[i];
                }
        }
        return best;
}

void
find_test(RB_Relation rel, const char *desc)
{
        RB_t tree;
        fail_unless(RB_Init(&tree), NULL);
        for (int i = 0; i < ARRAYSIZE(sparse); ++i) {
                fail_unless(RB_Insert(&tree, sparse[i]) != NULL, NULL);
        }
        RB_Check(tree.root);
        for (int i = -1; i <= 50; ++i) {
                int *found = RB_Find(&tree, i, rel);
                int arr = array_find(sparse, ARRAYSIZE(sparse), i, rel);
                if (arr == -1) {
                        if (found)
                                fail("Find returned %d, which is not %s %d",
                                     *found, desc, i);
                } else {
                        if (!found)
                                fail("Find returned nothing as %s %d, expected %d",
                                     desc, i, arr);
                        else if (*found != arr)
                                fail("Find returned %d as %s %d, expected %d",
                                     *found, desc, i, arr);
                }
        }
        RB_Check(tree.root);
        fail_unless(valueCount == ARRAYSIZE(sparse), NULL);
        RB_Release(&tree);
}

START_TEST(find_eq)
{
        find_test(RB_EQ, "==");
}
END_TEST;

START_TEST(find_lt)
{
        find_test(RB_LT, "<");
}
END_TEST;

START_TEST(find_ge)
{
        find_test(RB_GE, ">=");
}
END_TEST;

int int_compare(const void *a_in, const void *b_in)
{
    int *a = (int *) a_in;
    int *b = (int *) b_in;
    if (*a < *b)
        return -1;
    if (*a == *b)
        return 0;
    return 1;
}

START_TEST(scan_forward_and_back)
{
        RB_t tree;
        int sparseSorted[ARRAYSIZE(sparse)];
        RB_Iterator_t *it;

        memcpy(sparseSorted, sparse, sizeof(sparse));
        qsort(sparseSorted, ARRAYSIZE(sparseSorted),
              sizeof(sparseSorted[0]),
              int_compare);
        
        fail_unless(RB_Init(&tree), NULL);
        for (int i = 0; i < ARRAYSIZE(sparse); ++i) {
                fail_unless(RB_Insert(&tree, sparse[i]) != NULL, NULL);
        }

        /* forward scan */
        it = RB_First(&tree);
        fail_unless(it != NULL);
        for (int i = 0; i < ARRAYSIZE(sparse); ++i) {
            fail_unless(*RB_Iterator_Value(it) == sparseSorted[i],
                        "forward[%d]: expected %d got %d",
                        i, sparseSorted[i], *RB_Iterator_Value(it));
            fail_unless(RB_Iterator_HasPrev(it) == (i != 0));
            fail_unless(RB_Iterator_HasNext(it) == (i != ARRAYSIZE(sparse)-1));
            if (i != ARRAYSIZE(sparse)-1)
                fail_unless(RB_Iterator_Next(it));
            else
                fail_if(RB_Iterator_Next(it));
        }
        RB_Iterator_Release(it);


        /* reverse scan */
        it = RB_Last(&tree);
        fail_unless(it != NULL);        
        for (int i = ARRAYSIZE(sparse)-1; i >= 0; --i) {
            fail_unless(*RB_Iterator_Value(it) == sparseSorted[i],
                        "reverse[%d]: expected %d got %d",
                        i, sparseSorted[i], *RB_Iterator_Value(it));
            fail_unless(RB_Iterator_HasPrev(it) == (i != 0));
            fail_unless(RB_Iterator_HasNext(it) == (i != ARRAYSIZE(sparse)-1));
            if (i != 0)
                fail_unless(RB_Iterator_Prev(it));
            else
                fail_if(RB_Iterator_Prev(it));
        }
        RB_Iterator_Release(it);

        RB_Release(&tree);
}
END_TEST;

START_TEST(find_and_scan)
{
        RB_t tree;
        int sparseSorted[ARRAYSIZE(sparse)];
        RB_Iterator_t *it;

        memcpy(sparseSorted, sparse, sizeof(sparse));
        qsort(sparseSorted, ARRAYSIZE(sparseSorted),
              sizeof(sparseSorted[0]),
              int_compare);
        
        fail_unless(RB_Init(&tree), NULL);
        for (int i = 0; i < ARRAYSIZE(sparse); ++i) {
                fail_unless(RB_Insert(&tree, sparse[i]) != NULL, NULL);
        }

        for (int j = 0; j < ARRAYSIZE(sparse); ++j) {            
            /* forward scan */
            it = RB_FindIter(&tree, sparseSorted[j], RB_EQ);
            fail_unless(it != NULL);
            for (int i = j; i < ARRAYSIZE(sparse); ++i) {
                fail_unless(*RB_Iterator_Value(it) == sparseSorted[i],
                            "forward[%d]: expected %d got %d",
                            i, sparseSorted[i], *RB_Iterator_Value(it));
                fail_unless(RB_Iterator_HasPrev(it) == (i != 0));
                fail_unless(RB_Iterator_HasNext(it) == (i != ARRAYSIZE(sparse)-1));
                if (i != ARRAYSIZE(sparse)-1)
                    fail_unless(RB_Iterator_Next(it));
                else
                    fail_if(RB_Iterator_Next(it));
            }
            RB_Iterator_Release(it);


            /* reverse scan */
            it = RB_FindIter(&tree, sparseSorted[j], RB_EQ);
            fail_unless(it != NULL);        
            for (int i = j; i >= 0; --i) {
                fail_unless(*RB_Iterator_Value(it) == sparseSorted[i],
                            "reverse[%d]: expected %d got %d",
                            i, sparseSorted[i], *RB_Iterator_Value(it));
                fail_unless(RB_Iterator_HasPrev(it) == (i != 0));
                fail_unless(RB_Iterator_HasNext(it) == (i != ARRAYSIZE(sparse)-1));
                if (i != 0)
                    fail_unless(RB_Iterator_Prev(it));
                else
                    fail_if(RB_Iterator_Prev(it));
            }
            RB_Iterator_Release(it);
        }

        RB_Release(&tree);
}
END_TEST;

START_TEST(release_frees_all)
{
        RB_t tree;
        fail_unless(RB_Init(&tree), NULL);
        fail_unless(valueCount == 0, NULL);
        for (int i = 0; i < 10; ++i) {
                fail_unless(RB_Insert(&tree, i) != NULL, NULL);
        }
        fail_unless(valueCount == 10, NULL);
        RB_Release(&tree);
        fail_unless(valueCount == 0, NULL);
        RB_Release(&tree);
        fail_unless(valueCount == 0, NULL);
}
END_TEST;

START_TEST(stress)
{
        RB_t tree;
        char values[100] = {};

#define MAX_BIT      (8 * sizeof values)
#define TEST_BIT(i)  (values[(i)/8] & (1 << ((i)%8)))
#define SET_BIT(i)   (values[(i)/8] |= 1 << ((i)%8))
#define RESET_BIT(i) (values[(i)/8] &= ~(1 << ((i)%8)))
        fail_unless(RB_Init(&tree), NULL);
        srand(42);
        for (int iter = 0; iter < 50000; ++iter) {
                int value = rand() % MAX_BIT;
                if (TEST_BIT(value)) {
                        // Check that it's in the tree
                        fail_if(RB_Find(&tree, value, RB_EQ) == NULL, NULL);
                        // Remove it
                        RB_Remove(&tree, value);
                        RESET_BIT(value);
                        RB_Check(tree.root);
                } else {
                        // Check that it's not in the tree
                        fail_if(RB_Find(&tree, value, RB_EQ), NULL);
                        // Insert it
                        RB_Insert(&tree, value);
                        SET_BIT(value);
                        RB_Check(tree.root);
                }
        }
}
END_TEST;

int
main(void)
{
        Suite *s = suite_create("rbtree");

        TCase *tc = tcase_create("Core");
        tcase_add_test(tc, init);
        tcase_add_test(tc, insert);
        tcase_add_test(tc, find_eq);
        tcase_add_test(tc, find_lt);
        tcase_add_test(tc, find_ge);
        tcase_add_test(tc, scan_forward_and_back);
        tcase_add_test(tc, find_and_scan);
        tcase_add_test(tc, release_frees_all);
        tcase_add_test(tc, stress);
        tcase_set_timeout(tc, 60);
        suite_add_tcase(s, tc);

        return Test_Main(s);
}

// Annoying things about check:
//
// check should not evaluate the message format arguments unless
// there's actually a failure.  For example, if the test relates to
// the NULL-ness of a pointer.
//
// In no-fork mode, a failure should result in the immediate return
// from the test case because later code may assume that the tested
// condition passed.  This could be done with longjmp if necessary.
