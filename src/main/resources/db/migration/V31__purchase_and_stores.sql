-- =============================================================================================
-- Purchase and stores: the legacy Inventory and Purchase menus, on the one document model.
--
--   Store requisition (SR) ─┬─► Purchase requisition (SPR) ─► Purchase order (PO) ─► MRR ─► Purchase return
--                           └─► Material issue (against the SR, or direct)
--   Direct receive · Stock adjustment
--   Transfer request ─► Transfer issue ─► Transfer receive           (general items, between stores)
--   Fabric transfer issue ─► Fabric transfer receive                   (fabric lots, between stores)
--
-- Requisitions, orders, transfer requests and adjustments go through the approval matrix; what
-- physically moves stock (MRR, return, issue, receive, transfer issue/receive) is posted by the
-- store in one step and undone only by cancelling, which writes exact reversing rows - the same
-- rule the fabric chain (V29) follows.
--
-- General items get their own ledger beside the fabric one: inv_item_moves (append-only) and
-- inv_item_balances (running quantity and value per store and item, at moving weighted-average
-- cost, locked and updated in the same transaction as the move). Only ItemStockService writes
-- them. inv_periods closes a month's stock to further postings.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- Document header facts the purchase and store documents need.
--   to_warehouse      transfers: the store the stock goes to (warehouse_id is where it leaves)
--   purchase_type     PO: Direct, Spot or Import - the legacy poType
--   requisition_type  SR: Personal, Departmental or Production
--   department        SR: who asked
--   invoice_no        MRR: the supplier's invoice (the challan is reference_no)
--   lead_time_days    SR, SPR, PO
-- ---------------------------------------------------------------------------------------------
ALTER TABLE gbl_business_documents
    ADD COLUMN to_warehouse_id   BIGINT,
    ADD COLUMN purchase_type     VARCHAR(10),
    ADD COLUMN requisition_type  VARCHAR(15),
    ADD COLUMN department        VARCHAR(100),
    ADD COLUMN invoice_no        VARCHAR(60),
    ADD COLUMN lead_time_days    INTEGER;

ALTER TABLE gbl_business_documents
    ADD CONSTRAINT fk_gbd_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES org_warehouses (id),
    ADD CONSTRAINT ck_gbd_purchase_type CHECK (purchase_type IS NULL OR purchase_type IN ('DIRECT', 'SPOT', 'IMPORT')),
    ADD CONSTRAINT ck_gbd_requisition_type CHECK (requisition_type IS NULL
        OR requisition_type IN ('PERSONAL', 'DEPARTMENTAL', 'PRODUCTION')),
    ADD CONSTRAINT ck_gbd_lead_time CHECK (lead_time_days IS NULL OR lead_time_days >= 0),
    -- A transfer goes somewhere else.
    ADD CONSTRAINT ck_gbd_transfer_stores CHECK (to_warehouse_id IS NULL OR warehouse_id IS NULL
        OR to_warehouse_id <> warehouse_id);

CREATE INDEX ix_gbd_to_warehouse ON gbl_business_documents (to_warehouse_id) WHERE to_warehouse_id IS NOT NULL;

-- An item line: which brand and model was asked for or received, its specification and origin.
ALTER TABLE gbl_business_document_line_groups
    ADD COLUMN item_brand_id       BIGINT,
    ADD COLUMN item_model_id       BIGINT,
    ADD COLUMN item_specification  VARCHAR(500),
    ADD COLUMN origin_country      VARCHAR(60);

ALTER TABLE gbl_business_document_line_groups
    ADD CONSTRAINT fk_gbdlg_item_brand FOREIGN KEY (item_brand_id) REFERENCES inv_item_brands (id),
    ADD CONSTRAINT fk_gbdlg_item_model FOREIGN KEY (item_model_id) REFERENCES inv_item_models (id);

CREATE INDEX ix_gbdlg_item_brand ON gbl_business_document_line_groups (item_brand_id) WHERE item_brand_id IS NOT NULL;
CREATE INDEX ix_gbdlg_item_model ON gbl_business_document_line_groups (item_model_id) WHERE item_model_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_gbdlg_item ON gbl_business_document_line_groups (item_id) WHERE item_id IS NOT NULL;

-- A stock adjustment line adds or takes away (quantities stay positive, as everywhere else); an
-- MRR line records the condition the goods arrived in.
ALTER TABLE gbl_business_document_color_lines
    ADD COLUMN stock_direction  VARCHAR(3),
    ADD COLUMN condition_note   VARCHAR(300);

ALTER TABLE gbl_business_document_color_lines
    ADD CONSTRAINT ck_gbdcl_stock_direction CHECK (stock_direction IS NULL OR stock_direction IN ('IN', 'OUT'));

-- ---------------------------------------------------------------------------------------------
-- The general item ledger.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE inv_item_balances (
    warehouse_id     BIGINT         NOT NULL,
    item_id          BIGINT         NOT NULL,
    organization_id  BIGINT         NOT NULL,
    quantity         NUMERIC(20, 6) NOT NULL DEFAULT 0,
    value            NUMERIC(20, 6) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP      NOT NULL DEFAULT now(),

    PRIMARY KEY (warehouse_id, item_id),
    CONSTRAINT fk_item_balance_store FOREIGN KEY (warehouse_id) REFERENCES org_warehouses (id),
    CONSTRAINT fk_item_balance_item  FOREIGN KEY (item_id) REFERENCES inv_items (id),
    -- A store never holds less than nothing, and nothing is worth less than nothing.
    CONSTRAINT ck_item_balance CHECK (quantity >= 0 AND value >= 0)
);

CREATE INDEX ix_item_balance_item ON inv_item_balances (item_id);
CREATE INDEX ix_item_balance_org ON inv_item_balances (organization_id);

CREATE TABLE inv_item_moves (
    id                BIGSERIAL      PRIMARY KEY,
    organization_id   BIGINT         NOT NULL,
    warehouse_id      BIGINT         NOT NULL,
    item_id           BIGINT         NOT NULL,
    -- The date the stock moved: the document's date for a posting, the day of cancelling for a reversal.
    move_date         DATE           NOT NULL,
    quantity          NUMERIC(20, 6) NOT NULL,
    unit_cost         NUMERIC(20, 6) NOT NULL,
    value             NUMERIC(20, 6) NOT NULL,
    move_type         VARCHAR(30)    NOT NULL,
    document_id       BIGINT         NOT NULL,
    line_id           BIGINT,
    reverses_move_id  BIGINT,
    remarks           VARCHAR(300),
    posted_by         VARCHAR(100),
    posted_at         TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT fk_item_move_store    FOREIGN KEY (warehouse_id) REFERENCES org_warehouses (id),
    CONSTRAINT fk_item_move_item     FOREIGN KEY (item_id) REFERENCES inv_items (id),
    CONSTRAINT fk_item_move_document FOREIGN KEY (document_id) REFERENCES gbl_business_documents (id),
    CONSTRAINT fk_item_move_line     FOREIGN KEY (line_id) REFERENCES gbl_business_document_color_lines (id),
    CONSTRAINT fk_item_move_reverses FOREIGN KEY (reverses_move_id) REFERENCES inv_item_moves (id),
    CONSTRAINT ck_item_move_nonzero  CHECK (quantity <> 0),
    CONSTRAINT ck_item_move_cost     CHECK (unit_cost >= 0),
    -- In moves carry value in, out moves carry it out.
    CONSTRAINT ck_item_move_sign     CHECK (value = 0 OR sign(value) = sign(quantity)),
    CONSTRAINT ck_item_move_type CHECK (move_type IN
        ('RECEIPT', 'PURCHASE_RETURN', 'ISSUE', 'DIRECT_RECEIVE', 'TRANSFER_OUT', 'TRANSFER_IN',
         'ADJUST_IN', 'ADJUST_OUT', 'REVERSAL'))
);

CREATE INDEX ix_item_move_item ON inv_item_moves (item_id, warehouse_id, move_date, id);
CREATE INDEX ix_item_move_document ON inv_item_moves (document_id);
CREATE INDEX ix_item_move_line ON inv_item_moves (line_id) WHERE line_id IS NOT NULL;
CREATE INDEX ix_item_move_store_date ON inv_item_moves (organization_id, warehouse_id, move_date);
CREATE INDEX ix_item_move_reverses ON inv_item_moves (reverses_move_id) WHERE reverses_move_id IS NOT NULL;
CREATE UNIQUE INDEX uk_item_move_reversed_once ON inv_item_moves (reverses_move_id) WHERE reverses_move_id IS NOT NULL;

CREATE OR REPLACE FUNCTION inv_item_moves_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The item stock ledger is append-only: cancel the document to reverse move %', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_item_moves_append_only
    BEFORE UPDATE OR DELETE ON inv_item_moves
    FOR EACH ROW EXECUTE FUNCTION inv_item_moves_append_only();

-- ---------------------------------------------------------------------------------------------
-- Inventory periods - the legacy Period screen. A month with no row is open; closing it stops
-- every store posting dated in it until it is reopened, with a reason.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE inv_periods (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    period_month     DATE         NOT NULL,
    status           VARCHAR(10)  NOT NULL,
    closed_by        VARCHAR(100),
    closed_at        TIMESTAMP,
    reopened_by      VARCHAR(100),
    reopened_at      TIMESTAMP,
    remarks          VARCHAR(500),
    version          BIGINT,

    CONSTRAINT fk_inv_period_org FOREIGN KEY (organization_id) REFERENCES org_organizations (id),
    CONSTRAINT uk_inv_period UNIQUE (organization_id, period_month),
    CONSTRAINT ck_inv_period_first CHECK (EXTRACT(DAY FROM period_month) = 1),
    CONSTRAINT ck_inv_period_status CHECK (status IN ('OPEN', 'CLOSED'))
);

-- ---------------------------------------------------------------------------------------------
-- Fabric lots move between stores too.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE inv_fabric_moves DROP CONSTRAINT ck_fabric_move_type;
ALTER TABLE inv_fabric_moves ADD CONSTRAINT ck_fabric_move_type CHECK (move_type IN
    ('GREIGE_RECEIVE', 'GREIGE_ISSUE', 'FINISHED_RECEIVE', 'DELIVERY', 'TRANSFER_OUT', 'TRANSFER_IN', 'REVERSAL'));

-- ---------------------------------------------------------------------------------------------
-- Accounts: goods sent back to a supplier reverse the goods-received liability (V19's GRN rule,
-- the other way round). Seeded only where the ASFL chart is in place, as V19 does.
-- ---------------------------------------------------------------------------------------------
INSERT INTO acc_posting_rules (organization_id, code, name, event_type, effective_from, description,
                               active, deleted, version, created_by, created_at)
SELECT o.id, 'ASFL-PURCHASE_RETURN', 'Goods returned to a supplier', 'PURCHASE_RETURN', DATE '2000-01-01',
       'ASFL chart rule (V31)', TRUE, FALSE, 0, 'V31', now()
FROM org_organizations o
WHERE NOT EXISTS (SELECT 1 FROM acc_posting_rules x
                  WHERE x.organization_id = o.id AND x.event_type = 'PURCHASE_RETURN'
                    AND x.effective_to IS NULL AND x.active AND NOT x.deleted)
  AND EXISTS (SELECT 1 FROM acc_accounts a WHERE a.organization_id = o.id AND a.code = '20200104' AND a.usage_type <> 'SUMMARY')
  AND EXISTS (SELECT 1 FROM acc_accounts a WHERE a.organization_id = o.id AND a.code = '12010102' AND a.usage_type <> 'SUMMARY')
ON CONFLICT (organization_id, code) DO NOTHING;

INSERT INTO acc_posting_rule_lines (organization_id, rule_id, sequence, side, account_code, amount_key, version, created_by, created_at)
SELECT p.organization_id, p.id, leg.sequence, leg.side, leg.account_code, 'amount', 0, 'V31', now()
FROM acc_posting_rules p
CROSS JOIN (VALUES (1, 'DEBIT', '20200104'), (2, 'CREDIT', '12010102')) AS leg (sequence, side, account_code)
WHERE p.code = 'ASFL-PURCHASE_RETURN'
  AND NOT EXISTS (SELECT 1 FROM acc_posting_rule_lines l WHERE l.rule_id = p.id);

-- ---------------------------------------------------------------------------------------------
-- Screens, on the two working roles V8 set up (as V13 did for the item master): operations
-- raise and post everything; approvers see everything and sign what goes through approval.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.code, TRUE, TRUE, TRUE, TRUE, FALSE, 0, 'seed', now()
FROM sec_fabric_roles r,
     (VALUES ('SR'), ('SPR'), ('PO'), ('MRR'), ('PRT'), ('MI'), ('MR'), ('ST'), ('TI'), ('TRC'), ('SA'),
             ('FTI'), ('FTR'), ('ITEM_STOCK'), ('INV_PERIOD')) AS s(code)
WHERE r.name = 'ROLE_FABRIC_OPERATION'
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.code, TRUE, FALSE, FALSE, FALSE, s.approves, 0, 'seed', now()
FROM sec_fabric_roles r,
     (VALUES ('SR', TRUE), ('SPR', TRUE), ('PO', TRUE), ('ST', TRUE), ('SA', TRUE), ('INV_PERIOD', TRUE),
             ('MRR', FALSE), ('PRT', FALSE), ('MI', FALSE), ('MR', FALSE), ('TI', FALSE), ('TRC', FALSE),
             ('FTI', FALSE), ('FTR', FALSE), ('ITEM_STOCK', FALSE)) AS s(code, approves)
WHERE r.name = 'ROLE_DOCUMENT_APPROVER'
ON CONFLICT (role_id, screen_code) DO NOTHING;

-- Whoever keeps the fabric stores also moves fabric between them.
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, s.code, g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
CROSS JOIN (VALUES ('FTI'), ('FTR')) AS s(code)
WHERE g.screen_code = 'GR'
ON CONFLICT (role_id, screen_code) DO NOTHING;
