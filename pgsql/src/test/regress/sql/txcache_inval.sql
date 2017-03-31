-- setup
CREATE TABLE foo (a int, b int);
CREATE TABLE bar (c int);
CREATE TABLE baz (d int, e text);
INSERT INTO foo values (1,1);
INSERT INTO foo VALUES (1,2);
INSERT INTO foo VALUES (1,3);
INSERT INTO foo VALUES (2,1);
INSERT INTO bar VALUES (1);
INSERT INTO bar VALUES (2);
INSERT INTO bar VALUES (3);
INSERT INTO bar VALUES (4);
INSERT INTO bar VALUES (5);
INSERT INTO baz VALUES (1, 'foo');
INSERT INTO baz VALUES (2, 'bar');
INSERT INTO baz VALUES (2, 'baz');
INSERT INTO baz VALUES (3, 'baz');
CREATE INDEX foo_a on foo(a);
CREATE INDEX foo_b on foo(b);
CREATE INDEX bar_c on bar(c);
CREATE INDEX baz_d on baz(d);
CREATE INDEX baz_e on baz(e);
CREATE INDEX baz_de on baz(d,e);
SET enable_seqscan='off';

-- test invalidation tags on queries
BEGIN READ ONLY ISOLATION LEVEL SERIALIZABLE;
PIN;
\set QUIET off
SELECT * FROM foo WHERE a=1;
SELECT * FROM foo WHERE a=2;
SELECT * FROM foo WHERE a=3;
SELECT * FROM foo WHERE b=1;
SELECT * FROM bar WHERE c=1;
SELECT * FROM bar WHERE c>2;
SELECT * FROM bar;
SELECT * FROM baz WHERE d=1;
SELECT * FROM baz WHERE d=2;
SELECT * FROM baz WHERE e='bar';
SELECT * FROM baz WHERE e='baz';
SELECT * FROM baz WHERE e='baz' AND d=3;
COMMIT;
\set QUIET on

-- clear invalidations
SELECT * FROM pg_invalidations;
SELECT * FROM pg_invalidations;

-- test invalidations
BEGIN ISOLATION LEVEL SERIALIZABLE;
INSERT INTO bar VALUES (10);
COMMIT;
SELECT * FROM pg_invalidations;

BEGIN ISOLATION LEVEL SERIALIZABLE;
INSERT INTO foo VALUES (1,2);
DELETE FROM bar WHERE c = 1;
DELETE FROM baz WHERE d = 1;
COMMIT;
BEGIN ISOLATION LEVEL SERIALIZABLE;
UPDATE foo SET b=10 WHERE a=1;
COMMIT;
BEGIN ISOLATION LEVEL SERIALIZABLE;
INSERT INTO foo VALUES (1,2);
COMMIT;
BEGIN ISOLATION LEVEL SERIALIZABLE;
INSERT INTO baz VALUES (100,'quux');
COMMIT;
SELECT * FROM pg_invalidations;
SELECT * FROM pg_invalidations;

-- test null invalidation
-- this transaction isn't serializable, so it shouldn't cause an
-- invalidation, but should bump the xstamp, requiring a null inval.
BEGIN;
INSERT INTO foo VALUES (5,5);
COMMIT;
BEGIN READ ONLY ISOLATION LEVEL SERIALIZABLE;
PIN;
COMMIT;
SELECT * FROM pg_invalidations;
SELECT * FROM pg_invalidations;

-- cleanup
DROP TABLE foo;
DROP TABLE bar;
DROP TABLE baz;
UNPIN 6;
UNPIN 20;
UNPIN 26;
SET enable_seqscan='on';