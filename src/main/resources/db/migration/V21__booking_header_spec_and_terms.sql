-- Booking, completed: the header fields, specification fields and terms & conditions the
-- legacy /booking/index screen carries and this model did not.
--
-- Field list recovered from the legacy create form (business-logic-capture/extracted.json,
-- controller "booking") and checked against a real costing payload
-- (business-logic-capture/costing-api/18102503583.json) and the saved booking it produced.

-- ---------------------------------------------------------------------------------------------
-- Header. Generic columns, not Booking-only: a BPO carries the same booking type, brand,
-- garments, marketing person and price basis, inherited from the Booking it is raised against.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_documents
    ADD COLUMN booking_type        VARCHAR(20),
    ADD COLUMN order_type          VARCHAR(20),
    ADD COLUMN brand_id            BIGINT,
    ADD COLUMN garments_id         BIGINT,
    ADD COLUMN garments_address    VARCHAR(500),
    ADD COLUMN pre_cost_buyer      VARCHAR(150),
    ADD COLUMN price_in_meter      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN marketing_person_id BIGINT,
    ADD CONSTRAINT fk_gbd_brand            FOREIGN KEY (brand_id)            REFERENCES pty_parties (id),
    ADD CONSTRAINT fk_gbd_garments         FOREIGN KEY (garments_id)         REFERENCES pty_parties (id),
    ADD CONSTRAINT fk_gbd_marketing_person FOREIGN KEY (marketing_person_id) REFERENCES sec_fabric_users (id);

CREATE INDEX ix_gbd_brand            ON gbl_business_documents (brand_id)            WHERE brand_id IS NOT NULL;
CREATE INDEX ix_gbd_garments         ON gbl_business_documents (garments_id)         WHERE garments_id IS NOT NULL;
CREATE INDEX ix_gbd_marketing_person ON gbl_business_documents (marketing_person_id) WHERE marketing_person_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Specification (FabricSpec). finish_type widens because the legacy field is a multi-select.
-- quoted_price / break_even_price are snapshots taken from the costing system on every save,
-- so a booking keeps the figures it was priced against even after the costing is amended.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_document_line_groups
    ALTER COLUMN finish_type TYPE VARCHAR(300),
    ADD COLUMN costing_amendment_no     VARCHAR(10),
    ADD COLUMN fabric_source            VARCHAR(20),
    ADD COLUMN finish_type_ref          VARCHAR(500),
    ADD COLUMN quality_reference        VARCHAR(120),
    ADD COLUMN style_reference          VARCHAR(120),
    ADD COLUMN quoted_price             NUMERIC(20,6),
    ADD COLUMN break_even_price         NUMERIC(20,6),
    ADD COLUMN warp_yarn_name           VARCHAR(120),
    ADD COLUMN weft_yarn_name           VARCHAR(120),
    ADD COLUMN light_source_type        VARCHAR(20),
    ADD COLUMN base_material            VARCHAR(40),
    ADD COLUMN swatch_no                VARCHAR(60),
    ADD COLUMN lc_tenure                VARCHAR(30),
    ADD COLUMN lc_payment_type          VARCHAR(30),
    ADD COLUMN lead_time_days           INTEGER,
    ADD COLUMN target_quality_parameter VARCHAR(200),
    ADD COLUMN item_description         VARCHAR(1000);

CREATE INDEX ix_gbdlg_costing_code ON gbl_business_document_line_groups (organization_id, costing_code)
    WHERE costing_code IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Global terms & conditions: the organization's standard clauses per document type. Clauses
-- flagged default are copied onto every new document of that type, where they can be edited,
-- removed or added to. The legacy screen is /termsAndConditions/index.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE gbl_terms_conditions (
    id              BIGSERIAL     PRIMARY KEY,
    organization_id BIGINT        NOT NULL,
    condition_type  VARCHAR(30)   NOT NULL,
    caption         VARCHAR(150)  NOT NULL,
    body_text       VARCHAR(2000) NOT NULL,
    is_default      BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP
);

CREATE INDEX ix_gtc_org_type ON gbl_terms_conditions (organization_id, condition_type, active)
    WHERE deleted = FALSE;

-- ---------------------------------------------------------------------------------------------
-- A document's own clauses - asgdynamic's dtlTcSet (bodyText + serialNumber). Copied, not
-- referenced: editing the global clause later must not rewrite what a buyer already agreed to.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE gbl_business_document_terms (
    id              BIGSERIAL     PRIMARY KEY,
    organization_id BIGINT        NOT NULL,
    document_id     BIGINT        NOT NULL,
    serial_no       INTEGER       NOT NULL,
    body_text       VARCHAR(2000) NOT NULL,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT fk_gbdt_document FOREIGN KEY (document_id)
        REFERENCES gbl_business_documents (id) ON DELETE CASCADE
);

CREATE INDEX ix_gbdt_document ON gbl_business_document_terms (document_id);
CREATE INDEX ix_gbdt_org      ON gbl_business_document_terms (organization_id);

-- ---------------------------------------------------------------------------------------------
-- The terms setup screen, for whoever maintains fabric setup today.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'TERMS', g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'FABRIC_SETUP'
ON CONFLICT (role_id, screen_code) DO NOTHING;

-- ---------------------------------------------------------------------------------------------
-- Standard Booking clauses, as they appear on legacy bookings. The two general ones are
-- defaults; the hand-feel clause is buyer-specific, so it is offered, not imposed.
-- ---------------------------------------------------------------------------------------------
INSERT INTO gbl_terms_conditions
    (organization_id, condition_type, caption, body_text, is_default, sort_order, version, created_by, created_at)
SELECT o.id, c.condition_type, c.caption, c.body_text, c.is_default, c.sort_order, 0, 'seed', now()
FROM org_organizations o,
     (VALUES ('BOOKING', 'Dead yarn & naps',  'Dead Yarn & Naps Should Be Not Allowed.',                      TRUE,  1),
             ('BOOKING', 'TAP turnaround',    'Pls Provide TAP within 48 Hours after receiving the BPO.',     TRUE,  2),
             ('BOOKING', 'Soft hand feel',    'Buyer Required Better Soft Hand Feel.',                         FALSE, 3))
         AS c(condition_type, caption, body_text, is_default, sort_order);
