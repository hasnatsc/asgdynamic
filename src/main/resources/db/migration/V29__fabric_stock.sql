-- Production to delivery, part 3 of 3: stores and the fabric stock ledger.
--
-- Fabric here is made to order, so stock is never generic cloth: every metre sits in a LOT that
-- names its production order.
--   GREIGE lot    production order + fabric line (construction-keyed routes) or + colour line
--   FINISHED lot  production order colour line + dye lot + shade + grade (buyers reject mixed
--                 shades; B grade must never go out as A)
--
-- inv_fabric_moves is the append-only ledger: one row per posted line, never edited; cancelling a
-- document adds exact reversing rows. inv_fabric_balances is the running balance per store and
-- lot, locked and updated in the same transaction as the move, and it can never go negative or
-- promise more than it holds. inv_fabric_reservations is what each approved delivery order holds.
-- Only FabricStockService writes these four tables.

-- A store's role decides which documents may use it. The user's mill receives both greige and
-- dyed fabric into its Processing (dyeing) store, so a store may hold both.
ALTER TABLE org_warehouses
    ADD COLUMN holds_greige   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN holds_finished BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE org_warehouses SET holds_greige = TRUE, holds_finished = TRUE WHERE code = 'SAF001';   -- Processing Store
UPDATE org_warehouses SET holds_greige = TRUE                        WHERE code = 'SAF002';   -- Weaving Store

CREATE TABLE inv_fabric_lots (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    stage            VARCHAR(10)  NOT NULL,
    bpo_document_id  BIGINT       NOT NULL,
    line_group_id    BIGINT       NOT NULL,
    color_line_id    BIGINT,
    dye_lot          VARCHAR(40),
    shade            VARCHAR(40),
    grade            VARCHAR(2),
    created_by       VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_fabric_lot_bpo   FOREIGN KEY (bpo_document_id) REFERENCES gbl_business_documents (id),
    CONSTRAINT fk_fabric_lot_group FOREIGN KEY (line_group_id) REFERENCES gbl_business_document_line_groups (id),
    CONSTRAINT fk_fabric_lot_line  FOREIGN KEY (color_line_id) REFERENCES gbl_business_document_color_lines (id),
    CONSTRAINT ck_fabric_lot_stage CHECK (stage IN ('GREIGE', 'FINISHED')),
    CONSTRAINT ck_fabric_lot_grade CHECK (grade IS NULL OR grade IN ('A', 'B')),
    CONSTRAINT ck_fabric_lot_finished CHECK (stage = 'GREIGE' OR (color_line_id IS NOT NULL AND grade IS NOT NULL))
);

CREATE UNIQUE INDEX uk_fabric_lot ON inv_fabric_lots
    (organization_id, stage, line_group_id, COALESCE(color_line_id, 0),
     COALESCE(dye_lot, ''), COALESCE(shade, ''), COALESCE(grade, ''));
CREATE INDEX ix_fabric_lot_bpo ON inv_fabric_lots (bpo_document_id);
CREATE INDEX ix_fabric_lot_line ON inv_fabric_lots (color_line_id) WHERE color_line_id IS NOT NULL;

CREATE TABLE inv_fabric_balances (
    warehouse_id       BIGINT         NOT NULL,
    lot_id             BIGINT         NOT NULL,
    organization_id    BIGINT         NOT NULL,
    quantity           NUMERIC(20, 6) NOT NULL DEFAULT 0,
    reserved_quantity  NUMERIC(20, 6) NOT NULL DEFAULT 0,
    rolls              INTEGER        NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP      NOT NULL DEFAULT now(),

    PRIMARY KEY (warehouse_id, lot_id),
    CONSTRAINT fk_fabric_balance_store FOREIGN KEY (warehouse_id) REFERENCES org_warehouses (id),
    CONSTRAINT fk_fabric_balance_lot   FOREIGN KEY (lot_id) REFERENCES inv_fabric_lots (id),
    -- The store can never hold less than nothing, nor promise more than it holds.
    CONSTRAINT ck_fabric_balance CHECK (reserved_quantity >= 0 AND quantity >= reserved_quantity)
);

CREATE INDEX ix_fabric_balance_lot ON inv_fabric_balances (lot_id);

CREATE TABLE inv_fabric_moves (
    id                BIGSERIAL      PRIMARY KEY,
    organization_id   BIGINT         NOT NULL,
    warehouse_id      BIGINT         NOT NULL,
    lot_id            BIGINT         NOT NULL,
    quantity          NUMERIC(20, 6) NOT NULL,
    rolls             INTEGER        NOT NULL DEFAULT 0,
    uom_id            BIGINT,
    move_type         VARCHAR(30)    NOT NULL,
    document_id       BIGINT         NOT NULL,
    color_line_id     BIGINT,
    reverses_move_id  BIGINT,
    remarks           VARCHAR(300),
    posted_by         VARCHAR(100),
    posted_at         TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT fk_fabric_move_store    FOREIGN KEY (warehouse_id) REFERENCES org_warehouses (id),
    CONSTRAINT fk_fabric_move_lot      FOREIGN KEY (lot_id) REFERENCES inv_fabric_lots (id),
    CONSTRAINT fk_fabric_move_document FOREIGN KEY (document_id) REFERENCES gbl_business_documents (id),
    CONSTRAINT fk_fabric_move_reverses FOREIGN KEY (reverses_move_id) REFERENCES inv_fabric_moves (id),
    CONSTRAINT ck_fabric_move_nonzero  CHECK (quantity <> 0),
    CONSTRAINT ck_fabric_move_type CHECK (move_type IN
        ('GREIGE_RECEIVE', 'GREIGE_ISSUE', 'FINISHED_RECEIVE', 'DELIVERY', 'REVERSAL'))
);

CREATE INDEX ix_fabric_move_document ON inv_fabric_moves (document_id);
CREATE INDEX ix_fabric_move_lot ON inv_fabric_moves (lot_id, posted_at);
CREATE UNIQUE INDEX uk_fabric_move_reversed_once ON inv_fabric_moves (reverses_move_id) WHERE reverses_move_id IS NOT NULL;

CREATE OR REPLACE FUNCTION inv_fabric_moves_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The fabric stock ledger is append-only: cancel the document to reverse move %', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fabric_moves_append_only
    BEFORE UPDATE OR DELETE ON inv_fabric_moves
    FOR EACH ROW EXECUTE FUNCTION inv_fabric_moves_append_only();

CREATE TABLE inv_fabric_reservations (
    id               BIGSERIAL      PRIMARY KEY,
    organization_id  BIGINT         NOT NULL,
    color_line_id    BIGINT         NOT NULL,
    warehouse_id     BIGINT         NOT NULL,
    lot_id           BIGINT         NOT NULL,
    quantity         NUMERIC(20, 6) NOT NULL,
    updated_at       TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT fk_fabric_reservation_line  FOREIGN KEY (color_line_id) REFERENCES gbl_business_document_color_lines (id),
    CONSTRAINT fk_fabric_reservation_store FOREIGN KEY (warehouse_id) REFERENCES org_warehouses (id),
    CONSTRAINT fk_fabric_reservation_lot   FOREIGN KEY (lot_id) REFERENCES inv_fabric_lots (id),
    CONSTRAINT ck_fabric_reservation CHECK (quantity >= 0),
    CONSTRAINT uk_fabric_reservation UNIQUE (color_line_id, warehouse_id, lot_id)
);

-- The lot a store document's line names (Greige issue, delivery order and fabrics delivery pick
-- one; the two receives are given the lot they create).
ALTER TABLE gbl_business_document_color_lines
    ADD CONSTRAINT fk_gbdcl_fabric_lot FOREIGN KEY (fabric_lot_id) REFERENCES inv_fabric_lots (id);
