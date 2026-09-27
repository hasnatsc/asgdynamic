-- =============================================================================================
-- Commercial: export and import PI, LC and CI - the legacy Commercial menu on the document model.
--
--   Export   Delivery schedule ─► Export PI ─► Export LC ─► Export CI ─► realization
--   Import   Purchase requisition (or items) ─► Import PI ─┬─► Import LC (bill of entry, costs)
--                                                          └─► Purchase order ─► MRR at landed cost
--
-- Every commercial document is a gbl_business_documents row, so numbering, the approval matrix,
-- history and revisions (amendments) are the ones every other document uses. What only commercial
-- paper carries lives beside it:
--   com_document_details  one row per commercial document: banks and accounts, LC numbers and
--                         dates, tenure, payment and INCO terms, weights, HS code, bill of entry...
--   com_document_events   the dated records kept against a document after it is raised: UD and UP,
--                         back-to-back raw material LCs, sales contracts, required documents,
--                         costs, CI realization steps, import PI milestones.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- The company itself as a party, so its own bank accounts (the PI's advising account, the export
-- LC's beneficiary account, the import LC's local account) are party bank accounts like any other
-- - kept on the party screen, not in a second bank-account master.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE org_organizations ADD COLUMN self_party_id BIGINT;

INSERT INTO pty_parties (organization_id, code, name, legal_name, active, deleted, version, created_by, created_at)
SELECT o.id, 'SELF', o.name, o.name, TRUE, FALSE, 0, 'V32', now()
FROM org_organizations o
WHERE NOT EXISTS (SELECT 1 FROM pty_parties p WHERE p.organization_id = o.id AND p.code = 'SELF');

UPDATE org_organizations o SET self_party_id = p.id
FROM pty_parties p WHERE p.organization_id = o.id AND p.code = 'SELF' AND o.self_party_id IS NULL;

ALTER TABLE org_organizations
    ADD CONSTRAINT fk_org_self_party FOREIGN KEY (self_party_id) REFERENCES pty_parties (id);

-- ---------------------------------------------------------------------------------------------
-- Masters: document names (what an LC requires, what a CI carries) and cost heads.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE com_document_names (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(150) NOT NULL,
    doc_kind         VARCHAR(4)   NOT NULL,
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT uk_com_doc_name_code UNIQUE (organization_id, code),
    CONSTRAINT ck_com_doc_name_kind CHECK (doc_kind IN ('PI', 'LC', 'CI'))
);

CREATE TABLE com_cost_heads (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(150) NOT NULL,
    doc_kind         VARCHAR(4)   NOT NULL,
    -- The ledger account the cost is charged to, by code (as posting rules name accounts).
    account_code     VARCHAR(40),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT uk_com_cost_head_code UNIQUE (organization_id, code),
    CONSTRAINT ck_com_cost_head_kind CHECK (doc_kind IN ('PI', 'LC', 'CI'))
);

-- The documents an export LC calls for and a CI is presented with - the legacy CI's print list.
INSERT INTO com_document_names (organization_id, code, name, doc_kind, sort_order, version, created_by, created_at)
SELECT o.id, v.code, v.name, 'LC', v.sort_order, 0, 'V32', now()
FROM org_organizations o,
     (VALUES ('DN001', 'Bank Forwarding', 1), ('DN002', 'Bill of Exchange', 2), ('DN003', 'Commercial Invoice', 3),
             ('DN004', 'Delivery Challan', 4), ('DN005', 'Packing List', 5), ('DN006', 'Truck Receipt', 6),
             ('DN007', 'Certificate of Origin', 7), ('DN008', 'Beneficiary Certificate', 8),
             ('DN009', 'Azo Free Certificate', 9), ('DN010', 'Twenty Yard Certificate', 10),
             ('DN011', 'GRN', 11)) AS v(code, name, sort_order)
ON CONFLICT (organization_id, code) DO NOTHING;

INSERT INTO com_cost_heads (organization_id, code, name, doc_kind, version, created_by, created_at)
SELECT o.id, v.code, v.name, v.kind, 0, 'V32', now()
FROM org_organizations o,
     (VALUES ('CH001', 'Customs Duty', 'LC'), ('CH002', 'Freight Charge', 'LC'), ('CH003', 'Insurance', 'LC'),
             ('CH004', 'Bank Commission & Charges', 'LC'), ('CH005', 'C&F Charge', 'LC'),
             ('CH006', 'Bank Commission & Charges', 'CI'), ('CH007', 'Courier Charges', 'CI')) AS v(code, name, kind)
ON CONFLICT (organization_id, code) DO NOTHING;

-- ---------------------------------------------------------------------------------------------
-- A commercial document's own header facts.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE com_document_details (
    document_id              BIGINT         PRIMARY KEY,
    organization_id          BIGINT         NOT NULL,
    validity_date            DATE,
    shipment_date            DATE,
    issue_date               DATE,
    lc_no                    VARCHAR(80),
    master_lc_no             VARCHAR(80),
    master_lc_date           DATE,
    tenure                   VARCHAR(20),
    payment_terms            VARCHAR(30),
    delivery_terms           VARCHAR(10),
    -- Our bank and account: the PI's advising bank, the export LC's beneficiary, the import LC's local bank.
    bank_party_id            BIGINT,
    bank_account_id          BIGINT,
    -- The other side's bank: the export LC buyer's bank; the import LC supplier's beneficiary bank.
    counter_bank_party_id    BIGINT,
    counter_bank_account_id  BIGINT,
    foreign_bank_name        VARCHAR(200),
    foreign_bank_bin         VARCHAR(40),
    foreign_bank_swift       VARCHAR(20),
    foreign_bank_routing     VARCHAR(40),
    beneficiary_account_no   VARCHAR(60),
    hs_code_id               BIGINT,
    applicant_bond_licence   VARCHAR(80),
    net_weight               NUMERIC(14, 3),
    gross_weight             NUMERIC(14, 3),
    calc_net_weight          NUMERIC(14, 3),
    calc_gross_weight        NUMERIC(14, 3),
    amount_in_words          VARCHAR(500),
    partial_shipment         BOOLEAN        NOT NULL DEFAULT TRUE,
    btma_certificate         BOOLEAN        NOT NULL DEFAULT FALSE,
    acknowledged_on          DATE,
    ci_kind                  VARCHAR(10),
    ibc_no                   VARCHAR(60),
    realization_step         VARCHAR(30),
    import_doc_type          VARCHAR(10),
    lc_type                  VARCHAR(12),
    port                     VARCHAR(30),
    cnf_agent                VARCHAR(150),
    ip_no                    VARCHAR(60),
    sro_benefited            BOOLEAN        NOT NULL DEFAULT FALSE,
    btma_no                  VARCHAR(60),
    btma_date                DATE,
    bill_of_entry_no         VARCHAR(60),
    bill_of_entry_date       DATE,
    local_agent_party_id     BIGINT,
    -- An import LC opened back-to-back against an export LC.
    backed_by_document_id    BIGINT,
    incentive_amount         NUMERIC(20, 4),
    incentive_applied_on     DATE,
    incentive_confirmed_on   DATE,
    version                  BIGINT,

    CONSTRAINT fk_com_det_document   FOREIGN KEY (document_id) REFERENCES gbl_business_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_com_det_bank       FOREIGN KEY (bank_party_id) REFERENCES pty_parties (id),
    CONSTRAINT fk_com_det_account    FOREIGN KEY (bank_account_id) REFERENCES pty_party_bank_accounts (id),
    CONSTRAINT fk_com_det_cbank      FOREIGN KEY (counter_bank_party_id) REFERENCES pty_parties (id),
    CONSTRAINT fk_com_det_caccount   FOREIGN KEY (counter_bank_account_id) REFERENCES pty_party_bank_accounts (id),
    CONSTRAINT fk_com_det_hs         FOREIGN KEY (hs_code_id) REFERENCES inv_hs_codes (id),
    CONSTRAINT fk_com_det_agent      FOREIGN KEY (local_agent_party_id) REFERENCES pty_parties (id),
    CONSTRAINT fk_com_det_backed_by  FOREIGN KEY (backed_by_document_id) REFERENCES gbl_business_documents (id),
    CONSTRAINT ck_com_det_tenure     CHECK (tenure IS NULL OR tenure IN ('AT_SIGHT', 'D30', 'D60', 'D90', 'D120', 'D150', 'D180', 'TT')),
    CONSTRAINT ck_com_det_payment    CHECK (payment_terms IS NULL OR payment_terms IN
        ('AT_SIGHT', 'DATE_OF_DELIVERY', 'DATE_OF_ACCEPTANCE', 'DATE_OF_NEGOTIATION', 'DATE_OF_SHIPMENT', 'TT')),
    CONSTRAINT ck_com_det_delivery   CHECK (delivery_terms IS NULL OR delivery_terms IN ('EXW', 'FCA', 'CPT', 'CIP', 'FOB', 'CFR', 'CIF', 'DAP')),
    CONSTRAINT ck_com_det_ci_kind    CHECK (ci_kind IS NULL OR ci_kind IN ('REGULAR', 'ADVANCE')),
    CONSTRAINT ck_com_det_import_doc CHECK (import_doc_type IS NULL OR import_doc_type IN ('LC', 'TT', 'INVOICE')),
    CONSTRAINT ck_com_det_lc_type    CHECK (lc_type IS NULL OR lc_type IN ('DEFERRED', 'UPAS', 'UPAS_SPSM', 'EDF', 'AT_SIGHT')),
    CONSTRAINT ck_com_det_weights    CHECK ((net_weight IS NULL OR net_weight >= 0) AND (gross_weight IS NULL OR gross_weight >= 0)
        AND (net_weight IS NULL OR gross_weight IS NULL OR gross_weight >= net_weight)),
    CONSTRAINT ck_com_det_dates      CHECK (shipment_date IS NULL OR validity_date IS NULL OR shipment_date <= validity_date),
    CONSTRAINT ck_com_det_incentive  CHECK (incentive_amount IS NULL OR incentive_amount >= 0)
);

CREATE INDEX ix_com_det_bank ON com_document_details (bank_party_id) WHERE bank_party_id IS NOT NULL;
CREATE INDEX ix_com_det_account ON com_document_details (bank_account_id) WHERE bank_account_id IS NOT NULL;
CREATE INDEX ix_com_det_cbank ON com_document_details (counter_bank_party_id) WHERE counter_bank_party_id IS NOT NULL;
CREATE INDEX ix_com_det_caccount ON com_document_details (counter_bank_account_id) WHERE counter_bank_account_id IS NOT NULL;
CREATE INDEX ix_com_det_hs ON com_document_details (hs_code_id) WHERE hs_code_id IS NOT NULL;
CREATE INDEX ix_com_det_agent ON com_document_details (local_agent_party_id) WHERE local_agent_party_id IS NOT NULL;
CREATE INDEX ix_com_det_backed_by ON com_document_details (backed_by_document_id) WHERE backed_by_document_id IS NOT NULL;
CREATE INDEX ix_com_det_lc_no ON com_document_details (organization_id, lc_no) WHERE lc_no IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- What is recorded against a commercial document after it is raised.
--   UD / UP          utilization declaration / permission: number, date, value
--   BTB_LC           a back-to-back raw material LC: supplier, material (code), number, date, amount
--   SALES_CONTRACT   number and date
--   REQUIRED_DOC     a document the LC calls for (document_name_id)
--   COST             cost head, amount, date
--   REALIZATION      a CI's step (code): DOC_SUBMISSION ... FINAL_PAYMENT, dated, with an amount for
--                    PURCHASE and FINAL_PAYMENT
--   MILESTONE        an import PI's checkpoint (code): CED, CNF, BOND, PI_CORRECTED, LC_DRAFT, LC_CORRECTED
-- ---------------------------------------------------------------------------------------------
CREATE TABLE com_document_events (
    id                BIGSERIAL      PRIMARY KEY,
    organization_id   BIGINT         NOT NULL,
    document_id       BIGINT         NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    code              VARCHAR(30),
    ref_no            VARCHAR(100),
    event_date        DATE,
    amount            NUMERIC(20, 4),
    party_id          BIGINT,
    cost_head_id      BIGINT,
    document_name_id  BIGINT,
    remarks           VARCHAR(500),
    recorded_by       VARCHAR(100),
    recorded_at       TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT fk_com_event_document FOREIGN KEY (document_id) REFERENCES gbl_business_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_com_event_party    FOREIGN KEY (party_id) REFERENCES pty_parties (id),
    CONSTRAINT fk_com_event_cost     FOREIGN KEY (cost_head_id) REFERENCES com_cost_heads (id),
    CONSTRAINT fk_com_event_docname  FOREIGN KEY (document_name_id) REFERENCES com_document_names (id),
    CONSTRAINT ck_com_event_kind CHECK (kind IN
        ('UD', 'UP', 'BTB_LC', 'SALES_CONTRACT', 'REQUIRED_DOC', 'COST', 'REALIZATION', 'MILESTONE')),
    CONSTRAINT ck_com_event_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT ck_com_event_cost_head CHECK (kind <> 'COST' OR (cost_head_id IS NOT NULL AND amount IS NOT NULL)),
    CONSTRAINT ck_com_event_doc_name CHECK (kind <> 'REQUIRED_DOC' OR document_name_id IS NOT NULL),
    CONSTRAINT ck_com_event_code CHECK (kind NOT IN ('REALIZATION', 'MILESTONE', 'BTB_LC') OR code IS NOT NULL)
);

CREATE INDEX ix_com_event_document ON com_document_events (document_id, kind);
CREATE INDEX ix_com_event_party ON com_document_events (party_id) WHERE party_id IS NOT NULL;
CREATE INDEX ix_com_event_cost ON com_document_events (cost_head_id) WHERE cost_head_id IS NOT NULL;
CREATE INDEX ix_com_event_docname ON com_document_events (document_name_id) WHERE document_name_id IS NOT NULL;
-- A CI step, a milestone and a required document are recorded once.
CREATE UNIQUE INDEX uk_com_event_once ON com_document_events (document_id, kind, code)
    WHERE kind IN ('REALIZATION', 'MILESTONE');
CREATE UNIQUE INDEX uk_com_event_required_doc ON com_document_events (document_id, document_name_id)
    WHERE kind = 'REQUIRED_DOC';

-- ---------------------------------------------------------------------------------------------
-- Lines: a regular export CI line invoices one delivery challan line; an import MRR line carries
-- the duties and the share of LC costs that make its landed cost.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_document_color_lines
    ADD COLUMN delivery_line_id    BIGINT,
    ADD COLUMN customs_duty        NUMERIC(20, 6) NOT NULL DEFAULT 0,
    ADD COLUMN supplementary_duty  NUMERIC(20, 6) NOT NULL DEFAULT 0,
    ADD COLUMN allocated_cost      NUMERIC(20, 6) NOT NULL DEFAULT 0;

ALTER TABLE gbl_business_document_color_lines
    ADD CONSTRAINT fk_gbdcl_delivery_line FOREIGN KEY (delivery_line_id) REFERENCES gbl_business_document_color_lines (id),
    ADD CONSTRAINT ck_gbdcl_landed CHECK (customs_duty >= 0 AND supplementary_duty >= 0 AND allocated_cost >= 0);

CREATE INDEX ix_gbdcl_delivery_line ON gbl_business_document_color_lines (delivery_line_id) WHERE delivery_line_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Screens, as V31 granted the purchase and store screens.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.code, TRUE, TRUE, TRUE, TRUE, FALSE, 0, 'seed', now()
FROM sec_fabric_roles r, (VALUES ('EPI'), ('ELC'), ('ECI'), ('IPI'), ('ILC'), ('COM_SETUP'), ('COM_REGISTER')) AS s(code)
WHERE r.name = 'ROLE_FABRIC_OPERATION'
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.code, TRUE, FALSE, FALSE, FALSE, s.approves, 0, 'seed', now()
FROM sec_fabric_roles r,
     (VALUES ('EPI', TRUE), ('ELC', TRUE), ('ECI', TRUE), ('IPI', TRUE), ('ILC', TRUE), ('COM_SETUP', FALSE),
             ('COM_REGISTER', FALSE)) AS s(code, approves)
WHERE r.name = 'ROLE_DOCUMENT_APPROVER'
ON CONFLICT (role_id, screen_code) DO NOTHING;
