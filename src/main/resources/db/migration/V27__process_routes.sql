-- Production to delivery, part 1 of 3: the process routes master and the new screens.
--
-- The fabric type on a Booking line decides how the cloth is made and where it is delivered from.
-- That decision lives here, in data, so production can change a type's route or its allowances
-- without a release. A production order copies its line's route when it is saved (see
-- RouteSnapshot), so a later change to this table never rewrites an order already in progress.
--
--   route_code        GREIGE | YARN_DYED_GREIGE | DENIM_GREIGE | PIECE_DYED | FINISHED
--   needs_processing  a Dyeing work order is part of the route (PIECE_DYED, FINISHED)
--   process_kind      the Dyeing WO's default kind: DYE | PRINT | FINISH (REWORK is chosen by hand)
--   yarn_prep         NONE | YARN_DYE | INDIGO - flagged on the Weaving WO; its own WO is a later phase
--   greige_key        CONSTRUCTION: greige is woven per fabric line and dyed into its colours;
--                     COLOUR: the colour is in the yarn, so weaving stays per colour line
--   deliver_stage     GREIGE | FINISHED - which store the order is delivered from
--   *_pct             greige allowance over the finished quantity, receipt over the work order,
--                     delivery over the production order

CREATE TABLE fab_process_routes (
    id                      BIGSERIAL     PRIMARY KEY,
    organization_id         BIGINT        NOT NULL,
    fabric_type             VARCHAR(60)   NOT NULL,
    route_code              VARCHAR(30)   NOT NULL,
    needs_processing        BOOLEAN       NOT NULL,
    process_kind            VARCHAR(20),
    yarn_prep               VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    greige_key              VARCHAR(20)   NOT NULL,
    deliver_stage           VARCHAR(20)   NOT NULL,
    greige_allowance_pct    NUMERIC(6, 3) NOT NULL DEFAULT 0,
    receive_tolerance_pct   NUMERIC(6, 3) NOT NULL DEFAULT 5,
    delivery_tolerance_pct  NUMERIC(6, 3) NOT NULL DEFAULT 3,
    remarks                 VARCHAR(300),
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted                 BOOLEAN       NOT NULL DEFAULT FALSE,
    version                 BIGINT,
    created_by              VARCHAR(100),
    created_at              TIMESTAMP,
    updated_by              VARCHAR(100),
    updated_at              TIMESTAMP,

    CONSTRAINT fk_route_org FOREIGN KEY (organization_id) REFERENCES org_organizations (id),
    CONSTRAINT ck_route_code CHECK (route_code IN ('GREIGE', 'YARN_DYED_GREIGE', 'DENIM_GREIGE', 'PIECE_DYED', 'FINISHED')),
    CONSTRAINT ck_route_process_kind CHECK (process_kind IS NULL OR process_kind IN ('DYE', 'PRINT', 'FINISH', 'REWORK')),
    CONSTRAINT ck_route_yarn_prep CHECK (yarn_prep IN ('NONE', 'YARN_DYE', 'INDIGO')),
    CONSTRAINT ck_route_greige_key CHECK (greige_key IN ('CONSTRUCTION', 'COLOUR')),
    CONSTRAINT ck_route_deliver_stage CHECK (deliver_stage IN ('GREIGE', 'FINISHED')),
    CONSTRAINT ck_route_processing CHECK (needs_processing = (deliver_stage = 'FINISHED')),
    CONSTRAINT ck_route_pcts CHECK (greige_allowance_pct BETWEEN 0 AND 100
                                    AND receive_tolerance_pct BETWEEN 0 AND 100
                                    AND delivery_tolerance_pct BETWEEN 0 AND 100)
);

-- One live route per fabric type, matched case-insensitively (the Booking stores the type as text).
CREATE UNIQUE INDEX uk_route_org_fabric_type
    ON fab_process_routes (organization_id, lower(btrim(fabric_type)))
    WHERE deleted = FALSE;

-- Seeded for every organization from the 20 fabric types V2 seeded, per the design's routing table.
INSERT INTO fab_process_routes
    (organization_id, fabric_type, route_code, needs_processing, process_kind, yarn_prep, greige_key,
     deliver_stage, greige_allowance_pct, receive_tolerance_pct, delivery_tolerance_pct, version, created_by, created_at)
SELECT o.id, r.fabric_type, r.route_code, r.deliver_stage = 'FINISHED', r.process_kind, r.yarn_prep, r.greige_key,
       r.deliver_stage, r.allowance, 5, 3, 0, 'seed', now()
FROM org_organizations o
CROSS JOIN (VALUES
    ('Greige Solid Dyed',            'GREIGE',           NULL,     'NONE',     'CONSTRUCTION', 'GREIGE',    2),
    ('Greige Solid Dyed Lungi',      'GREIGE',           NULL,     'NONE',     'CONSTRUCTION', 'GREIGE',    2),
    ('Greige Solid Dyed (LUNGI)',    'GREIGE',           NULL,     'NONE',     'CONSTRUCTION', 'GREIGE',    2),
    ('Greige Solid Dyed Spandex',    'GREIGE',           NULL,     'NONE',     'CONSTRUCTION', 'GREIGE',    2),
    ('Greige Yarn Dyed',             'YARN_DYED_GREIGE', NULL,     'YARN_DYE', 'COLOUR',       'GREIGE',    2),
    ('Greige Yarn Dyed Spandex',     'YARN_DYED_GREIGE', NULL,     'YARN_DYE', 'COLOUR',       'GREIGE',    2),
    ('Yarn Dyed Lungi_Greige',       'YARN_DYED_GREIGE', NULL,     'YARN_DYE', 'COLOUR',       'GREIGE',    2),
    ('Yarn Dyed LUNGI (Greige)',     'YARN_DYED_GREIGE', NULL,     'YARN_DYE', 'COLOUR',       'GREIGE',    2),
    ('Greige Indigo Denim',          'DENIM_GREIGE',     NULL,     'INDIGO',   'COLOUR',       'GREIGE',    2),
    ('Greige Indigo Denim Spandex',  'DENIM_GREIGE',     NULL,     'INDIGO',   'COLOUR',       'GREIGE',    2),
    ('Solid Dyed',                   'PIECE_DYED',       'DYE',    'NONE',     'CONSTRUCTION', 'FINISHED', 10),
    ('Solid Dyed Spandex',           'PIECE_DYED',       'DYE',    'NONE',     'CONSTRUCTION', 'FINISHED', 10),
    ('Solid Dyed Print',             'PIECE_DYED',       'PRINT',  'NONE',     'CONSTRUCTION', 'FINISHED', 10),
    ('Solid Dyed Print Spandex',     'PIECE_DYED',       'PRINT',  'NONE',     'CONSTRUCTION', 'FINISHED', 10),
    ('Yarn Dyed',                    'FINISHED',         'FINISH', 'YARN_DYE', 'COLOUR',       'FINISHED',  8),
    ('Yarn Dyed Spandex',            'FINISHED',         'FINISH', 'YARN_DYE', 'COLOUR',       'FINISHED',  8),
    ('Yarn Dyed Print',              'FINISHED',         'PRINT',  'YARN_DYE', 'COLOUR',       'FINISHED',  8),
    ('Yarn Dyed Print Spandex',      'FINISHED',         'PRINT',  'YARN_DYE', 'COLOUR',       'FINISHED',  8),
    ('Indigo Denim',                 'FINISHED',         'FINISH', 'INDIGO',   'COLOUR',       'FINISHED',  8),
    ('Indigo Denim Spandex',         'FINISHED',         'FINISH', 'INDIGO',   'COLOUR',       'FINISHED',  8)
) AS r(fabric_type, route_code, process_kind, yarn_prep, greige_key, deliver_stage, allowance)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------
-- The new screens, granted from the screens whose people will use them:
--   Greige issue, Finished receive   <- Greige receive (the same store keepers, the same verbs)
--   Production board                 <- Production order (view)
--   Ready to deliver                 <- Delivery order (view)
--   Fabric stock                     <- anyone who may view a chain document (view); stock is
--                                       team-scoped on the server like every other screen
--   Process routes                   <- Fabric setup (the production head's master data)
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, s.code, g.can_view, g.can_create, g.can_amend, g.can_delete, g.can_approve, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
CROSS JOIN (VALUES ('GI'), ('FFR')) AS s(code)
WHERE g.screen_code = 'GR'
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT DISTINCT g.role_id, s.code, TRUE, FALSE, FALSE, FALSE, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
JOIN (VALUES ('BPO', 'PROD_BOARD'), ('DO', 'DELIVERY_BOARD')) AS s(source, code) ON g.screen_code = s.source
WHERE g.can_view OR g.can_create OR g.can_approve
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT DISTINCT g.role_id, 'FABRIC_STOCK', TRUE, FALSE, FALSE, FALSE, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code IN ('BPO', 'WWO', 'PWO', 'GR', 'RPI', 'DO', 'FD')
  AND (g.can_view OR g.can_create OR g.can_approve)
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'PROCESS_ROUTE', g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'FABRIC_SETUP'
ON CONFLICT (role_id, screen_code) DO NOTHING;
