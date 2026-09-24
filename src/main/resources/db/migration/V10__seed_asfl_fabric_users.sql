-- Seeds the two real warehouses (from the same ASFL admin export used to build HASSML's
-- V30/V31) and 37 real logins into sec_fabric_users, all under the 'AF' business unit V9
-- already created.
--
-- APPLY THIS BY HAND — Flyway is enabled here (spring.flyway.enabled=true, unlike HASSML),
-- but this file still needs applying by hand since it's not yet wired into the automatic
-- migration path the way V1-V9 are — same caveat V7/V8 already had this session.
--
-- NOT INCLUDED — no meaningful mapping exists, nothing invented:
--   • Roles. The source data's roles (ROLE_MARKETING_MANAGER, ROLE_COMMERCIAL_SETUP, etc.)
--     are HASSML/SpindleERP concepts — marketing, commercial, import. asgdynamic's Screen enum
--     only has the 10 fabric-document screens (BOOKING, BPO, RPI, WWO, PWO, GR, DO, FD,
--     FABRIC_SETUP, SECURITY_ADMIN) — there is no "marketing manager" screen to grant. Every
--     user below is created with zero roles; assign real ones via /setup/roles once it's clear
--     which of these 37 people actually touch fabric booking/production documents.
--   • Password: "123456" for everyone (bcrypt via pgcrypto), same as every other seed this
--     session — force a reset before real use.
--   • mehedi_commercial's second warehouse. The source lists them under both Processing Store
--     and Weaving Store; FabricUser.warehouseId is a single nullable id (see FabricUser's own
--     javadoc — multi-warehouse per user is explicitly "not implemented yet"), so only
--     Processing Store (their first-listed store) is set.
--
-- Idempotent — safe to re-run.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO org_warehouses (organization_id, business_unit_id, code, name, active, created_by, created_at)
SELECT (SELECT id FROM org_organizations WHERE code = 'ASG'),
       (SELECT id FROM org_business_units WHERE code = 'AF'),
       w.code, w.name, TRUE, 'seed', NOW()
FROM (VALUES
    ('SAF001', 'Processing Store'),
    ('SAF002', 'Weaving Store')
) AS w(code, name)
WHERE NOT EXISTS (SELECT 1 FROM org_warehouses WHERE code = w.code);

INSERT INTO sec_fabric_users (organization_id, username, password_hash, full_name,
                              business_unit_id, business_unit_code, warehouse_id,
                              account_locked, active, deleted, created_by, created_at, updated_at)
SELECT (SELECT id FROM org_organizations WHERE code = 'ASG'),
       u.username, crypt('123456', gen_salt('bf', 10)), u.full_name,
       (SELECT id FROM org_business_units WHERE code = 'AF'), 'AF',
       (SELECT id FROM org_warehouses WHERE code = u.warehouse_code),
       FALSE, TRUE, FALSE, 'seed', NOW(), NOW()
FROM (VALUES
    ('hasnat',               'Abul Hasnat',                     'SAF001'),
    ('rassel_marketing',     'Abu Naser Rassel',                'SAF001'),
    ('sabbir_marketing',     'Sabbir Hossain',                  'SAF001'),
    ('imran_marketing',      'Alif Imran',                      'SAF001'),
    ('anik_marketing',       'Anik Das',                        'SAF001'),
    ('tonoy_marketing',      'Fazlul Karim Tonoy',               'SAF001'),
    ('palash_marketing',     'Arifuzzaman Palash',              'SAF001'),
    ('russel_marketing',     'Russel Hossain',                  'SAF001'),
    ('sumon_marketing',      'Sumon Ahamed',                    'SAF001'),
    ('masud_marketing',      'Faqrul Islam Masud',              'SAF001'),
    ('topu_marketing',       'Zahirul Islam Topu',              'SAF001'),
    ('hemal_marketing',      'Majharul Hemal',                  'SAF001'),
    ('jubaer_marketing',     'Al Jubayer',                      'SAF001'),
    ('sibbir_marketing',     'Sibbir Ahmed',                    'SAF001'),
    ('masudrana_marketing',  'Masud Rana',                      'SAF001'),
    ('gobinda_marketing',    'Gobinda',                         'SAF001'),
    ('samad_marketing',      'Abdus Samad',                     'SAF001'),
    ('raihan_marketing',     'Mozaher Hossain Raihan',          'SAF001'),
    ('mohiuddin_marketing',  'Ismail Mohiuddin',                'SAF001'),
    ('rashel_marketing',     'Rashel Ahammed',                  'SAF001'),
    ('shakil_marketing',     'Shakil Ahmed',                    'SAF001'),
    ('ashraful_marketing',   'Ashraful Alam',                   'SAF001'),
    ('ashok_marketing',      'Ashok Kumar',                     'SAF001'),
    ('monirul_marketing',    'Monirul Islam',                   'SAF001'),
    ('noyem_planning',       'Noyem',                           'SAF001'),
    ('nurul_islam',          'Bir Muktijoddha Nurul Islam',     'SAF001'),
    ('mosharraf_commercial', 'Mohammad Mosharraf Hossain',      'SAF001'),
    ('arif_commercial',      'Md. Arif Hossain',                'SAF001'),
    ('aolad_commercial',     'Md. Aolad Hossain',               'SAF001'),
    ('sakib_import',         'Shams Al Sharif Sakib',           'SAF001'),
    ('dastagir_import',      'Mr. Dastagir',                    'SAF001'),
    ('inventory_import',     'Inventory',                       'SAF001'),
    ('planning',             'Planning',                        'SAF001'),
    ('weaving_store',        'Weaving Store',                   'SAF002'),
    ('processing_store',     'Processing Store',                'SAF001'),
    ('mehedi_commercial',    'Mehedi Hasan',                    'SAF001'),
    ('ashik_commercial',     'Mezbowl Ashik',                   'SAF001')
) AS u(username, full_name, warehouse_code)
WHERE NOT EXISTS (SELECT 1 FROM sec_fabric_users WHERE username = u.username);
