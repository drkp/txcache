-- Adds a virtual time base to a database that doesn't already have one.

CREATE TABLE virtual_time_base (
   virtual_time_base TIMESTAMP                                                  );

INSERT INTO virtual_time_base 
SELECT MAX(start_date) AS virtual_time_base FROM items;
