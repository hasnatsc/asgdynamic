-- The live asgdynamic item category tree (itemCategory/index), under the six roots V13 seeded.
--
-- Codes are copied exactly as the legacy system issued them, irregular ones included, because
-- documents already in circulation name them:
--   * CAF111201 numbers its group's first child 01, where the rule gives 11;
--   * SFB121113, SFB131101, SFB131301 carry an SFB prefix under CAF parents.
-- ItemCategoryService.nextCode reads only the digit tail of sibling codes, so it numbers new
-- children after these without clashing (the next child of CAF111200 is CAF111202, of
-- CAF121100 is CAF121114).
--
-- Item types on the item-level categories are inferred from their names - the legacy tree
-- carries none, and an item-level category must have one before items can be filed under it.
-- Groups are left untyped. Idempotent: an existing (organization, code) is left alone.

-- ---------------------------------------------------------------------------------------------
-- Groups, under the roots
-- ---------------------------------------------------------------------------------------------
INSERT INTO inv_item_categories (organization_id, code, prefix, name, layer, parent_id, version, created_by, created_at)
SELECT p.organization_id, v.code, 'CAF', v.name, 'GROUP', p.id, 0, 'seed', now()
FROM (VALUES
        ('CAF110000', 'CAF111100', 'Dyes & chemicals'),
        ('CAF110000', 'CAF111200', 'Yarn'),
        ('CAF110000', 'CAF111300', 'Packaging Material'),
        ('CAF120000', 'CAF121100', 'Fabrics'),
        ('CAF120000', 'CAF121300', 'Work in Progress (WIP)'),
        ('CAF130000', 'CAF131100', 'Maintenance, Repair, and Operating (MRO)'),
        ('CAF130000', 'CAF131200', 'Electrical and electronics'),
        ('CAF130000', 'CAF131300', 'Mechanical and Spares parts'),
        ('CAF130000', 'CAF131400', 'ETP'),
        ('CAF130000', 'CAF131500', 'Fire and Safety'),
        ('CAF130000', 'CAF131600', 'Fuel and Lubricants'),
        ('CAF130000', 'CAF131700', 'Workshop'),
        ('CAF140000', 'CAF141100', 'Printing and Stationary'),
        ('CAF140000', 'CAF141200', 'General'),
        ('CAF140000', 'CAF141300', 'Crockeries and cutleries'),
        ('CAF140000', 'CAF141400', 'Medicine'),
        ('CAF140000', 'CAF141500', 'IT Consumables'),
        ('CAF140000', 'CAF141600', 'Security Surveillance Consumables'),
        ('CAF150000', 'CAF151100', 'Building'),
        ('CAF150000', 'CAF151200', 'Land'),
        ('CAF150000', 'CAF151300', 'Furniture and Fixture'),
        ('CAF150000', 'CAF151400', 'Plant and Machinery'),
        ('CAF150000', 'CAF151500', 'Vehicles'),
        ('CAF150000', 'CAF151600', 'Tool Box'),
        ('CAF150000', 'CAF151700', 'Electric Equipments'),
        ('CAF150000', 'CAF151800', 'IT'),
        ('CAF150000', 'CAF151900', 'Security Surveillance'),
        ('CAF160000', 'CAF161100', 'Building Material'),
        ('CAF160000', 'CAF161200', 'Iron'),
        ('CAF160000', 'CAF161300', 'Cement'),
        ('CAF160000', 'CAF161400', 'Paint'),
        ('CAF160000', 'CAF161500', 'Brick, Sand and Stone')
     ) AS v(parent_code, code, name)
JOIN inv_item_categories p ON p.code = v.parent_code AND p.layer = 'ROOT' AND NOT p.deleted
JOIN org_organizations o ON o.id = p.organization_id AND o.code = 'ASG'
ON CONFLICT (organization_id, code) DO NOTHING;

-- ---------------------------------------------------------------------------------------------
-- Item-level categories, under the groups
-- ---------------------------------------------------------------------------------------------
INSERT INTO inv_item_categories (organization_id, code, prefix, name, layer, item_type, parent_id, version, created_by, created_at)
SELECT p.organization_id, v.code, left(v.code, 3), v.name, 'ITEM', v.item_type, p.id, 0, 'seed', now()
FROM (VALUES
        ('CAF111100', 'CAF111111', 'Dyes',                                     'CHEMICALS'),
        ('CAF111100', 'CAF111112', 'Chemicals',                                'CHEMICALS'),
        ('CAF111200', 'CAF111201', 'Yarn',                                     'YARN'),
        ('CAF121100', 'CAF121111', 'Yarn dyed fabrics',                        'FABRICS'),
        ('CAF121100', 'CAF121112', 'Solid dyed fabrics',                       'FABRICS'),
        ('CAF121100', 'SFB121113', 'AOP Fabrics',                              'FABRICS'),
        ('CAF131100', 'SFB131101', 'Maintenance, Repair, and Operating (MRO)', 'MRO'),
        ('CAF131300', 'SFB131301', 'Mechanical and Spares parts',              'MRO')
     ) AS v(parent_code, code, name, item_type)
JOIN inv_item_categories p ON p.code = v.parent_code AND p.layer = 'GROUP' AND NOT p.deleted
JOIN org_organizations o ON o.id = p.organization_id AND o.code = 'ASG'
ON CONFLICT (organization_id, code) DO NOTHING;
