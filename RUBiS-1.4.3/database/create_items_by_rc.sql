CREATE TABLE items_by_rc (
    itemid integer NOT NULL,
    region integer NOT NULL,
    category integer NOT NULL,
    end_date timestamp without time zone
);


ALTER TABLE public.items_by_rc OWNER TO cecchet;

INSERT INTO items_by_rc
SELECT items.id, users.region, items.category, items.end_date
FROM items, users where items.seller = users.id;

CREATE INDEX items_by_rc_index ON items_by_rc USING btree (region, category);

