BEGIN;
INSERT INTO old_items SELECT * FROM items WHERE end_date < start_date;
DELETE FROM items WHERE end_date < start_date;
COMMIT;
