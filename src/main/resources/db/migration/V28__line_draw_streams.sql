-- Production to delivery, part 2 of 3: one quantity counter per stream, and the columns the new
-- steps record.
--
-- Until now every document raised against a production order drew from the same
-- fulfilled_quantity on the BPO colour line, so a 10,000 m Weaving WO left nothing for the Dyeing
-- WO or the delivery schedule of the same line. A draw is now recorded against the parent line AND
-- the kind of document drawing it (the stream is the child's document type), so weaving, dyeing
-- and scheduling each see their own balance of the same line.
--
-- fulfilled_quantity stays, as a read-only mirror of each parent's principal stream (Booking <- BPO,
-- BPO <- delivery schedule, Weaving WO <- greige receive, Dyeing WO <- finished receive,
-- delivery schedule <- delivery order, delivery order <- fabrics delivery), capped at the line's
-- quantity. The Booking analytics reads the Booking one.

CREATE TABLE gbl_line_draws (
    id               BIGSERIAL      PRIMARY KEY,
    organization_id  BIGINT         NOT NULL,
    source_kind      VARCHAR(10)    NOT NULL,
    source_id        BIGINT         NOT NULL,
    stream           VARCHAR(40)    NOT NULL,
    drawn_quantity   NUMERIC(20, 6) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT ck_line_draw_kind CHECK (source_kind IN ('GROUP', 'COLOUR')),
    CONSTRAINT ck_line_draw_positive CHECK (drawn_quantity >= 0),
    CONSTRAINT uk_line_draw UNIQUE (source_kind, source_id, stream)
);

-- ---------------------------------------------------------------------------------------------
-- Colour lines: construction-keyed draws, what store documents record, short-close, revisions.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_document_color_lines
    ADD COLUMN source_line_group_id  BIGINT,
    ADD COLUMN fabric_lot_id         BIGINT,
    ADD COLUMN dye_lot               VARCHAR(40),
    ADD COLUMN shade                 VARCHAR(40),
    ADD COLUMN grade                 VARCHAR(2),
    ADD COLUMN rolls                 INTEGER,
    ADD COLUMN delivery_date         DATE,
    ADD COLUMN short_closed_quantity NUMERIC(20, 6) NOT NULL DEFAULT 0,
    ADD COLUMN short_close_reason    VARCHAR(300),
    ADD COLUMN revised_from_line_id  BIGINT;

ALTER TABLE gbl_business_document_color_lines
    ADD CONSTRAINT fk_gbdcl_source_line_group FOREIGN KEY (source_line_group_id)
        REFERENCES gbl_business_document_line_groups (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_gbdcl_grade CHECK (grade IS NULL OR grade IN ('A', 'B')),
    ADD CONSTRAINT ck_gbdcl_rolls CHECK (rolls IS NULL OR rolls >= 0),
    ADD CONSTRAINT ck_gbdcl_short_closed CHECK (short_closed_quantity >= 0);

CREATE INDEX ix_gbdcl_source_line_group ON gbl_business_document_color_lines (source_line_group_id)
    WHERE source_line_group_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Line groups: the route as it was when the production order was saved (RouteSnapshot).
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_document_line_groups
    ADD COLUMN route_code             VARCHAR(30),
    ADD COLUMN needs_processing       BOOLEAN,
    ADD COLUMN route_process_kind     VARCHAR(20),
    ADD COLUMN yarn_prep              VARCHAR(20),
    ADD COLUMN greige_key             VARCHAR(20),
    ADD COLUMN deliver_stage          VARCHAR(20),
    ADD COLUMN greige_allowance_pct   NUMERIC(6, 3),
    ADD COLUMN receive_tolerance_pct  NUMERIC(6, 3),
    ADD COLUMN delivery_tolerance_pct NUMERIC(6, 3),
    ADD COLUMN revised_from_group_id  BIGINT;

-- ---------------------------------------------------------------------------------------------
-- Document header facts the new steps need.
--   process_kind   Dyeing WO: DYE | PRINT | FINISH | REWORK
--   vendor         subcontracted weaving or dyeing
--   vehicle/driver Fabrics delivery's gate pass (the delivery address is garments_address)
--   batch_closed   Dyeing WO: closed, its unreturned greige booked as process loss
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_documents
    ADD COLUMN process_kind     VARCHAR(20),
    ADD COLUMN vendor_party_id  BIGINT,
    ADD COLUMN vehicle_no       VARCHAR(40),
    ADD COLUMN driver_name      VARCHAR(100),
    ADD COLUMN batch_closed     BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE gbl_business_documents
    ADD CONSTRAINT fk_gbd_vendor FOREIGN KEY (vendor_party_id) REFERENCES pty_parties (id),
    ADD CONSTRAINT ck_gbd_process_kind CHECK (process_kind IS NULL OR process_kind IN ('DYE', 'PRINT', 'FINISH', 'REWORK'));

-- ---------------------------------------------------------------------------------------------
-- Backfill: rebuild every stream from the child documents themselves. A child counts when it is
-- live (not deleted, not cancelled) and is the version that holds the draw: a revision holds none
-- until it is approved, and an approved revision takes over from the version it supersedes.
-- ---------------------------------------------------------------------------------------------
WITH holders AS (
    SELECT d.id, d.organization_id, d.document_type
    FROM gbl_business_documents d
    WHERE d.deleted = FALSE
      AND d.status <> 'CANCELLED'
      AND NOT (d.revision_no > 0 AND d.status IN ('DRAFT', 'SUBMITTED', 'REJECTED'))
      AND NOT EXISTS (
          SELECT 1 FROM gbl_business_documents n
          WHERE n.revision_of_id = COALESCE(d.revision_of_id, d.id)
            AND n.revision_no > d.revision_no
            AND n.deleted = FALSE
            AND n.status IN ('APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED'))
)
INSERT INTO gbl_line_draws (organization_id, source_kind, source_id, stream, drawn_quantity)
SELECT h.organization_id, 'COLOUR', cl.source_color_line_id, h.document_type, SUM(cl.quantity)
FROM gbl_business_document_color_lines cl
JOIN gbl_business_document_line_groups g ON g.id = cl.line_group_id
JOIN holders h ON h.id = g.document_id
WHERE cl.source_color_line_id IS NOT NULL
GROUP BY h.organization_id, cl.source_color_line_id, h.document_type;

-- The mirror: each parent line's fulfilled_quantity is its principal stream, capped at its quantity.
UPDATE gbl_business_document_color_lines cl
SET fulfilled_quantity = 0;

UPDATE gbl_business_document_color_lines cl
SET fulfilled_quantity = LEAST(ld.drawn_quantity, cl.quantity)
FROM gbl_line_draws ld,
     gbl_business_document_line_groups g,
     gbl_business_documents p
WHERE ld.source_kind = 'COLOUR'
  AND ld.source_id = cl.id
  AND g.id = cl.line_group_id
  AND p.id = g.document_id
  AND (p.document_type, ld.stream) IN (
      ('BOOKING', 'BULK_PRODUCTION_ORDER'),
      ('BULK_PRODUCTION_ORDER', 'REQUEST_FOR_PI'),
      ('WEAVING_WORK_ORDER', 'GREIGE_RECEIVE'),
      ('PROCESSING_WORK_ORDER', 'FINISHED_FABRICS_RECEIVE'),
      ('REQUEST_FOR_PI', 'DELIVERY_ORDER'),
      ('DELIVERY_ORDER', 'FABRICS_DELIVERY'));

-- The production board and the chain's reports read child documents by their type and parent.
CREATE INDEX IF NOT EXISTS ix_gbd_org_type_parent
    ON gbl_business_documents (organization_id, document_type, parent_document_id)
    WHERE deleted = FALSE;
