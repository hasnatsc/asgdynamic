-- =============================================================================================
-- A production order's pre-delivery schedule: what is to go to the buyer, when, and in what form -
-- a PP submission of 20 yards on the 25th, the full delivery of 18,630 on the 30th - planned on the
-- order before any delivery schedule is raised.
--
-- Delivery types are a master (Master data -> Delivery types): a code, a name and a sort order.
-- A row names the booking colour line the order's line was drawn from - that link survives the
-- order being edited or revised, where the order's own line ids do not.
-- =============================================================================================

CREATE TABLE fab_delivery_types (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT fk_fab_delivery_type_org FOREIGN KEY (organization_id) REFERENCES org_organizations (id)
);

-- A code is reused only once its type is deleted.
CREATE UNIQUE INDEX uk_fab_delivery_type_code ON fab_delivery_types (organization_id, upper(code)) WHERE deleted = FALSE;

INSERT INTO fab_delivery_types (organization_id, code, name, sort_order, version, created_by, created_at)
SELECT o.id, v.code, v.name, v.sort_order, 0, 'V36', now()
FROM org_organizations o,
     (VALUES ('PPS', 'PP Submission', 1), ('PARTIAL', 'Partial Delivery', 2), ('FULL', 'Full Delivery', 3))
         AS v(code, name, sort_order);

CREATE TABLE fab_bpo_pre_deliveries (
    id                    BIGSERIAL      PRIMARY KEY,
    organization_id       BIGINT         NOT NULL,
    document_id           BIGINT         NOT NULL,
    line_no               INTEGER        NOT NULL,
    delivery_type_id      BIGINT         NOT NULL,
    delivery_date         DATE           NOT NULL,
    -- The booking colour line the order's line comes from: the colour.
    source_color_line_id  BIGINT         NOT NULL,
    quantity              NUMERIC(20, 6) NOT NULL,
    serial_no             INTEGER        NOT NULL,
    created_by            VARCHAR(100),
    created_at            TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT fk_fab_pre_delivery_doc FOREIGN KEY (document_id) REFERENCES gbl_business_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_fab_pre_delivery_type FOREIGN KEY (delivery_type_id) REFERENCES fab_delivery_types (id),
    CONSTRAINT fk_fab_pre_delivery_colour FOREIGN KEY (source_color_line_id) REFERENCES gbl_business_document_color_lines (id),
    CONSTRAINT uk_fab_pre_delivery_line UNIQUE (document_id, line_no),
    CONSTRAINT ck_fab_pre_delivery_qty CHECK (quantity > 0),
    CONSTRAINT ck_fab_pre_delivery_serial CHECK (serial_no > 0)
);

CREATE INDEX ix_fab_pre_delivery_doc ON fab_bpo_pre_deliveries (document_id);
CREATE INDEX ix_fab_pre_delivery_type ON fab_bpo_pre_deliveries (delivery_type_id);

-- Whoever keeps the fabric masters keeps delivery types.
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'DELIVERY_TYPE', g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'V36', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'FABRIC_SETUP'
ON CONFLICT (role_id, screen_code) DO NOTHING;
