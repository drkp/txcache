#ifndef _LIB_TMPL_RBTREE_DECL_H_
#define _LIB_TMPL_RBTREE_DECL_H_

#include <stdbool.h>
#include <stddef.h>

%template RBTree RB(KEY_t, VALUE_t);

/*
 * Red-black trees.  Based on
 * http://www.eternallyconfuzzled.com/tuts/datastructures/jsw_tut_rbtree.aspx
 *
 * Modified and templatized by Austin Clements 2008-02-21
 */

/*
  Red Black balanced tree library

    > Created (Julienne Walker): August 23, 2003
    > Modified (Julienne Walker): March 14, 2008

  This code is in the public domain. Anyone may
  use it or change it in any way that they see
  fit. The author assumes no responsibility for 
  damages incurred through use of the original
  code or any variations thereof.

  It is requested, but not required, that due
  credit is given to the original author and
  anyone who has modified the code through
  a header comment, such as this one.
*/

struct RB_node_t;

/* May need to expand this if we want to support iterator-based
 * deletion. */
typedef struct RB_Iterator_t {
  struct RB_node_t *cur;
} RB_Iterator_t;

typedef struct RB_t {
  struct RB_node_t *root; /* Top of the tree */
  size_t size; /* Number of items */
  struct RB_node_t *first, *last;
} RB_t;

typedef enum {
  RB_EQ,
  RB_LT,
  RB_GE,
} RB_Relation;

/* Red Black tree functions */
bool RB_Init(RB_t *tree);
void RB_Release(RB_t *tree);
VALUE_t *RB_Find(RB_t *tree, KEY_t key, RB_Relation rel);
RB_Iterator_t *RB_FindIter(RB_t *tree, KEY_t key, RB_Relation rel);
VALUE_t *RB_Insert(RB_t *tree, KEY_t key);
bool RB_Remove(RB_t *tree, KEY_t key);
size_t RB_Size(RB_t *tree);
size_t RB_ElemSize(void);
RB_Iterator_t *RB_First(RB_t *tree);
RB_Iterator_t *RB_Last(RB_t *tree);
VALUE_t *RB_Iterator_Value(RB_Iterator_t *it);
bool RB_Iterator_HasNext(RB_Iterator_t *it);
bool RB_Iterator_HasPrev(RB_Iterator_t *it);
bool RB_Iterator_Next(RB_Iterator_t *it);
bool RB_Iterator_Prev(RB_Iterator_t *it);
void RB_Iterator_Release(RB_Iterator_t *it);

%endtemplate;

#endif // _LIB_TMPL_RBTREE_DECL_H_
