#ifndef _LIB_TMPL_RBTREE_IMPL_H_
#define _LIB_TMPL_RBTREE_IMPL_H_

#include <stdlib.h>
#if PARANOID
#include <stdio.h>
#include <assert.h>
#endif

%template RBTreeImpl RB(KEY_t, VALUE_t,
                        INIT_VALUE_func, RELEASE_VALUE_func,
                        COMPARE_VALUE_func);

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
*/

#ifndef HEIGHT_LIMIT
#define HEIGHT_LIMIT 64 /* Tallest allowable tree */
#endif

typedef struct RB_node_t {
  VALUE_t          data;
  // XXX Consider making this smaller than an int.
  int              red;      /* Color (1=red, 0=black) */
  struct RB_node_t *link[2]; /* Left (0) and right (1) links */
  struct RB_node_t *next, *prev; /* In-order chaining */
} RB_node_t;

#if PARANOID
static size_t RB_Count(RB_node_t *root);
#define CHECK_COUNT(x) assert(RB_Size(x) == RB_Count(x->root))
#else
#define CHECK_COUNT(x) do {} while (0)
#endif


/**
  <summary>
  Checks the color of a red black node
  <summary>
  <param name="root">The node to check</param>
  <returns>1 for a red node, 0 for a black node</returns>
  <remarks>For rbtree.c internal use only</remarks>
*/
static inline int RB_IsRed ( RB_node_t *root )
{
  return root != NULL && root->red == 1;
}

/**
  <summary>
  Performs a single red black rotation in the specified direction
  This function assumes that all nodes are valid for a rotation
  <summary>
  <param name="root">The original root to rotate around</param>
  <param name="dir">The direction to rotate (0 = left, 1 = right)</param>
  <returns>The new root ater rotation</returns>
  <remarks>For rbtree.c internal use only</remarks>
*/
static RB_node_t *RB_Single ( RB_node_t *root, int dir )
{
  RB_node_t *save = root->link[!dir];

  root->link[!dir] = save->link[dir];
  save->link[dir] = root;

  root->red = 1;
  save->red = 0;

  return save;
}

/**
  <summary>
  Performs a double red black rotation in the specified direction
  This function assumes that all nodes are valid for a rotation
  <summary>
  <param name="root">The original root to rotate around</param>
  <param name="dir">The direction to rotate (0 = left, 1 = right)</param>
  <returns>The new root after rotation</returns>
  <remarks>For rbtree.c internal use only</remarks>
*/
static RB_node_t *RB_Double ( RB_node_t *root, int dir )
{
  root->link[!dir] = RB_Single ( root->link[!dir], !dir );

  return RB_Single ( root, dir );
}

/**
  <summary>
  Creates an initializes a new red black node with freshly initialized
  data. This function does not insert the new node into a tree
  <summary>
  <param name="tree">The red black tree this node is being created for</param>
  <param name="key">The key that will be stored in this node</param>
  <returns>A pointer to the new node</returns>
  <remarks>
  For rbtree.c internal use only. The data for this node must
  be freed using the RELEASE_VALUE_func function. The returned pointer
  must be freed using C's free function
  </remarks>
*/
static RB_node_t *RB_NewNode ( RB_t *tree, KEY_t key )
{
  RB_node_t *rn = (RB_node_t *)malloc ( sizeof *rn );

  if ( rn == NULL )
    return NULL;

  rn->red = 1;
  INIT_VALUE_func(&rn->data, key);
  rn->link[0] = rn->link[1] = NULL;
  rn->prev = rn->next = NULL;

  return rn;
}

/**
  <summary>
  Initializes an empty red black tree
  <summary>
  <remarks>
  The initialized tree must be released with RB_Release
  </remarks>
*/
bool RB_Init ( RB_t *tree )
{
  tree->root = NULL;
  tree->size = 0;
  return true;
}

/**
  <summary>
  Releases a valid red black tree.  This is idempotent.
  <summary>
  <param name="tree">The tree to release</param>
  <remarks>
  The tree must have been initialized using RB_Init
  </remarks>
*/
void RB_Release ( RB_t *tree )
{
  if (tree->root) {
    RB_node_t *it = tree->root;
    RB_node_t *save;

    /*
      Rotate away the left links so that
      we can treat this like the destruction
      of a linked list
    */
    while ( it != NULL ) {
      if ( it->link[0] == NULL ) {
        /* No left links, just kill the node and move on */
        save = it->link[1];
        RELEASE_VALUE_func ( &it->data );
        free ( it );
      }
      else {
        /* Rotate away the left link and check again */
        save = it->link[0];
        it->link[0] = save->link[1];
        save->link[1] = it;
      }

      it = save;
    }

    tree->root = NULL;
  }
}

/**
  <summary>
  Search for a copy of the specified
  node data in a red black tree
  <summary>
  <param name="tree">The tree to search</param>
  <param name="key">The key value to search for</param>
  <returns>
  A pointer to the data value stored in the tree,
  or a null pointer if no data could be found
  </returns>
*/
static RB_node_t *RB_FindImpl ( RB_t *tree, KEY_t key, RB_Relation rel )
{
  RB_node_t *it = tree->root;
  RB_node_t *best = NULL;

  while ( it != NULL ) {
    int cmp = COMPARE_VALUE_func ( &it->data, key );

    switch (rel) {
    case RB_EQ:
      if ( cmp == 0 )
        return it;
      break;

    case RB_LT:
      if ( cmp < 0 )
        best = it;
      break;

    case RB_GE:
      if ( cmp == 0 )
        return it;
      if ( cmp > 0 )
        best = it;
      break;
    }

    it = it->link[cmp < 0];
  }

  return best;
}

VALUE_t *RB_Find( RB_t *tree, KEY_t key, RB_Relation rel )
{
  RB_node_t *best = RB_FindImpl(tree, key, rel);

  return best == NULL ? NULL : &best->data;
}

RB_Iterator_t *RB_FindIter( RB_t *tree, KEY_t key, RB_Relation rel )
{
  RB_node_t *best = RB_FindImpl(tree, key, rel);
  RB_Iterator_t *iter;

  if (best == NULL)
    return NULL;

  iter = malloc(sizeof(RB_Iterator_t));
  iter->cur = best;
  return iter;
}


/**
  <summary>
  Insert a newly initialized value into a red black
  tree at the specified key
  <summary>
  <param name="tree">The tree to insert into</param>
  <param name="key">The key to insert at</param>
  <returns>
  A pointer to the new value, or null if insertion
  failed for any reason
  </returns>
*/
VALUE_t *RB_Insert ( RB_t *tree, KEY_t key )
{
  CHECK_COUNT(tree);
  
  RB_node_t *result = NULL;

  if ( tree->root == NULL ) {
    /*
      We have an empty tree; attach the
      new node directly to the root
    */
    tree->root = RB_NewNode ( tree, key );

    if ( tree->root == NULL )
      return NULL;
    result = tree->root;
    tree->first = tree->last = tree->root;
    ++tree->size;
  }
  else {
    RB_node_t head = {.red=0}; /* False tree root */
    RB_node_t *g, *t;     /* Grandparent & parent */
    RB_node_t *p, *q;     /* Iterator & parent */
    int dir = 0, last = 0;

    /* Set up our helpers */
    t = &head;
    g = p = NULL;
    q = t->link[1] = tree->root;

    /* Search down the tree for a place to insert */
    for ( ; ; ) {
      if ( q == NULL ) {
        /* Insert a new node at the first null link */
        p->link[dir] = q = RB_NewNode ( tree, key );

        if ( q == NULL )
          return NULL;
        
        if (dir) {
          q->prev = p;
          q->next = p->next;
          p->next = q;
          if (q->next == NULL) {
            tree->last = q;
          } else {
            q->next->prev = q;
          }
        } else {
          q->next = p;
          q->prev = p->prev;
          p->prev = q;
          if (q->prev == NULL) {
            tree->first = q;
          } else {
            q->prev->next = q;
          }
        }
        result = q;
        ++tree->size;
      }
      else if ( RB_IsRed ( q->link[0] ) && RB_IsRed ( q->link[1] ) ) {
        /* Simple red violation: color flip */
        q->red = 1;
        q->link[0]->red = 0;
        q->link[1]->red = 0;
      }

      if ( RB_IsRed ( q ) && RB_IsRed ( p ) ) {
        /* Hard red violation: rotations necessary */
        int dir2 = t->link[1] == g;

        if ( q == p->link[last] )
          t->link[dir2] = RB_Single ( g, !last );
        else
          t->link[dir2] = RB_Double ( g, !last );
      }

      /*
        Stop working if we inserted a node. This
        check also disallows duplicates in the tree
      */
      if ( COMPARE_VALUE_func ( &q->data, key ) == 0 )
        break;

      last = dir;
      dir = COMPARE_VALUE_func ( &q->data, key ) < 0;

      /* Move the helpers down */
      if ( g != NULL )
        t = g;

      g = p, p = q;
      q = q->link[dir];
    }

    /* Update the root (it may be different) */
    tree->root = head.link[1];
  }

  /* Make the root black for simplified logic */
  tree->root->red = 0;

  return &result->data;
}

/**
  <summary>
  Remove a node from a red black tree
  that matches the user-specified data
  <summary>
  <param name="tree">The tree to remove from</param>
  <param name="key">The key to search for</param>
  <returns>
  true if the value was removed successfully,
  false if the removal failed for any reason
  </returns>
  <remarks>
  The most common failure reason should be
  that the data was not found in the tree
  </remarks>
*/
bool RB_Remove ( RB_t *tree, KEY_t key )
{
  CHECK_COUNT(tree);

  if ( tree->root != NULL ) {
    RB_node_t head = {.red=0}; /* False tree root */
    RB_node_t *q, *p, *g; /* Helpers */
    RB_node_t *f = NULL;  /* Found item */
    RB_node_t *fParent = NULL;
    int dir = 1;

    /* Set up our helpers */
    q = &head;
    g = p = NULL;
    q->link[1] = tree->root;

    /*
      Search and push a red node down
      to fix red violations as we go
    */
    while ( q->link[dir] != NULL ) {
      int last = dir;

      /* Move the helpers down */
      g = p, p = q;
      q = q->link[dir];
      dir = COMPARE_VALUE_func ( &q->data, key ) < 0;

      /*
        Save the node with matching data and keep
        going; we'll do removal tasks at the end
      */
      if ( COMPARE_VALUE_func ( &q->data, key ) == 0 )
        f = q;

#if PARANOID
#define CHECK_FPARENT() if (fParent) assert(fParent->link[0] == f || fParent->link[1] == f)
#define PN(n) printf(#n ": %p  link[0]=%p link[1]=%p\n", n, n ? n->link[0] : 0, n ? n->link[1] : 0);
#else
#define CHECK_FPARENT() do {} while (0)
#endif

      /* Push the red node down with rotations and color flips */
      if ( !RB_IsRed ( q ) && !RB_IsRed ( q->link[dir] ) ) {
        if ( RB_IsRed ( q->link[!dir] ) )
          {
          p = p->link[last] = RB_Single ( q, dir );
          CHECK_FPARENT();
        }
        else if ( !RB_IsRed ( q->link[!dir] ) ) {
          RB_node_t *s = p->link[!last];

          if ( s != NULL ) {
            if ( !RB_IsRed ( s->link[!last] ) && !RB_IsRed ( s->link[last] ) ) {
              /* Color flip */
              p->red = 0;
              s->red = 1;
              q->red = 1;
              CHECK_FPARENT();
            }
            else {
              // g is p's parent
              int dir2 = g->link[1] == p;

              if ( RB_IsRed ( s->link[last] ) )
                g->link[dir2] = RB_Double ( p, last );
              else if ( RB_IsRed ( s->link[!last] ) )
                g->link[dir2] = RB_Single ( p, last );
              if (g == fParent)
                fParent = g->link[dir2];
              CHECK_FPARENT();
              
              /* Ensure correct coloring */
              q->red = g->link[dir2]->red = 1;
              g->link[dir2]->link[0]->red = 0;
              g->link[dir2]->link[1]->red = 0;
            }
          }
        }
      }
      if (f == q)
        fParent = p;
    }

    /* Replace and remove the saved node */
    if ( f != NULL ) {
      // Move q to where f is
      RELEASE_VALUE_func ( &f->data );
      // This is the old code.  We can't assume copying will work
      // because there might be pointers into this link.
#if 0
      f->data = q->data;
      p->link[p->link[1] == q] =
        q->link[q->link[0] == NULL];
      free ( q );
#else
      // Remove q from where it is
      p->link[p->link[1] == q] =
        q->link[q->link[0] == NULL];

      // Replace the pointer to f with a pointer to q
      if (f != q) {
        CHECK_FPARENT();
        fParent->link[fParent->link[1] == f] = q;

        // q gets f's other properties
        q->red = f->red;
        q->link[0] = f->link[0];
        q->link[1] = f->link[1];
      }

      // Update order chain
      if (tree->first == f)
        tree->first = f->next;
      if (tree->last == f)
        tree->last = f->prev;
      if (f->prev)
        f->prev->next = f->next;
      if (f->next)
        f->next->prev = f->prev;

      free(f);
      --tree->size;
#endif
    }
#undef CHECK_FPARENT
#undef PN

    /* Update the root (it may be different) */
    tree->root = head.link[1];

    /* Make the root black for simplified logic */
    if ( tree->root != NULL )
      tree->root->red = 0;

  }

  return true;
}

/**
  <summary>
  Gets the number of nodes in a red black tree
  <summary>
  <param name="tree">The tree to calculate a size for</param>
  <returns>The number of nodes in the tree</returns>
*/
size_t RB_Size ( RB_t *tree )
{
  return tree->size;
}

#if PARANOID
static size_t RB_Count(RB_node_t *root)
{
  if (root == NULL) {
    return 0;
  }

  size_t n = 1;               /* one for self */

  /* Add counts of child subtrees */
  for (int i = 0; i < 2; i++) {
    n += RB_Count(root->link[i]);
  }
  return n;
}
#endif

size_t RB_ElemSize(void)
{
  return sizeof(RB_node_t);
}

RB_Iterator_t *RB_First(RB_t *tree)
{
  RB_Iterator_t *it = malloc(sizeof(RB_Iterator_t));
  if (it == NULL)
    return NULL;

  it->cur = tree->first;
  return it;
}

RB_Iterator_t *RB_Last(RB_t *tree)
{
  RB_Iterator_t *it = malloc(sizeof(RB_Iterator_t));
  if (it == NULL)
    return NULL;

  it->cur = tree->last;
  return it;
}

VALUE_t *RB_Iterator_Value(RB_Iterator_t *it)
{
  return &it->cur->data;
}

bool RB_Iterator_HasNext(RB_Iterator_t *it)
{
  return (it->cur->next != NULL);
}

bool RB_Iterator_HasPrev(RB_Iterator_t *it)
{
  return (it->cur->prev != NULL);
}

bool RB_Iterator_Next(RB_Iterator_t *it)
{
  if (it->cur->next == NULL)
    return false;
  
  it->cur = it->cur->next;
  return true;
}

bool RB_Iterator_Prev(RB_Iterator_t *it)
{
  if (it->cur->prev == NULL)
    return false;
  
  it->cur = it->cur->prev;
  return true;
}

void RB_Iterator_Release(RB_Iterator_t *it)
{
  free(it);
}

%endtemplate;

#endif // _LIB_TMPL_RBTREE_IMPL_H_
