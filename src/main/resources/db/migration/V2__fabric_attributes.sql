-- Fabric reference lists.
--
-- Values are the real ones in use, recovered from the asgdynamic screens
-- (see business-logic-capture/master_data_catalog.json). Seeded for organization 1;
-- adjust the organization_id if you bootstrap a different tenant first.

CREATE TABLE fab_attributes (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    attribute_type  VARCHAR(30)  NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(300),
    display_order   INTEGER      NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_fab_attr_org_type_code UNIQUE (organization_id, attribute_type, code)
);

CREATE INDEX ix_fab_attr_lookup ON fab_attributes (organization_id, attribute_type, active);
CREATE INDEX ix_fab_attr_org    ON fab_attributes (organization_id);

-- WEAVE_TYPE: 31 values
INSERT INTO fab_attributes (organization_id, attribute_type, code, name, display_order, created_by, created_at) VALUES
  (1, 'WEAVE_TYPE', '21STWILL', '2/1 S Twill', 10, 'seed', now()),
  (1, 'WEAVE_TYPE', '21ZTWILL', '2/1 Z Twill', 20, 'seed', now()),
  (1, 'WEAVE_TYPE', '22MATT', '2/2 Matt', 30, 'seed', now()),
  (1, 'WEAVE_TYPE', '22STWILL', '2/2 S Twill', 40, 'seed', now()),
  (1, 'WEAVE_TYPE', '22ZTWILL', '2/2 Z Twill', 50, 'seed', now()),
  (1, 'WEAVE_TYPE', '31BROKENTWIL', '3/1 Broken Twill', 60, 'seed', now()),
  (1, 'WEAVE_TYPE', '31STWILL', '3/1 S Twill', 70, 'seed', now()),
  (1, 'WEAVE_TYPE', '31ZTWILL', '3/1 Z Twill', 80, 'seed', now()),
  (1, 'WEAVE_TYPE', '41SATIN', '4/1 Satin', 90, 'seed', now()),
  (1, 'WEAVE_TYPE', 'DOBBY', 'Dobby', 100, 'seed', now()),
  (1, 'WEAVE_TYPE', 'OXFORD', 'Oxford', 110, 'seed', now()),
  (1, 'WEAVE_TYPE', 'PLAIN', 'Plain', 120, 'seed', now()),
  (1, 'WEAVE_TYPE', 'RIBSTOP', 'Rib Stop', 130, 'seed', now()),
  (1, 'WEAVE_TYPE', '11', '1/1', 140, 'seed', now()),
  (1, 'WEAVE_TYPE', '14', '1/4', 150, 'seed', now()),
  (1, 'WEAVE_TYPE', '15', '1/5', 160, 'seed', now()),
  (1, 'WEAVE_TYPE', '16', '1/6', 170, 'seed', now()),
  (1, 'WEAVE_TYPE', '17', '1/7', 180, 'seed', now()),
  (1, 'WEAVE_TYPE', '18', '1/8', 190, 'seed', now()),
  (1, 'WEAVE_TYPE', '21', '2/1', 200, 'seed', now()),
  (1, 'WEAVE_TYPE', '22', '2/2', 210, 'seed', now()),
  (1, 'WEAVE_TYPE', '31', '3/1', 220, 'seed', now()),
  (1, 'WEAVE_TYPE', '32', '3/2', 230, 'seed', now()),
  (1, 'WEAVE_TYPE', '41', '4/1', 240, 'seed', now()),
  (1, 'WEAVE_TYPE', '42', '4/2', 250, 'seed', now()),
  (1, 'WEAVE_TYPE', '51', '5/1', 260, 'seed', now()),
  (1, 'WEAVE_TYPE', '61', '6/1', 270, 'seed', now()),
  (1, 'WEAVE_TYPE', '71', '7/1', 280, 'seed', now()),
  (1, 'WEAVE_TYPE', 'DOBBYLONGFLO', 'Dobby : Long Float', 290, 'seed', now()),
  (1, 'WEAVE_TYPE', 'DOBBYMEDIUMF', 'Dobby : Medium Float', 300, 'seed', now()),
  (1, 'WEAVE_TYPE', 'DOBBYSHORTFL', 'Dobby : Short Float', 310, 'seed', now());

-- WEAVE_STYLE: 14 values
INSERT INTO fab_attributes (organization_id, attribute_type, code, name, display_order, created_by, created_at) VALUES
  (1, 'WEAVE_STYLE', 'BROKENTWILL', 'Broken Twill', 10, 'seed', now()),
  (1, 'WEAVE_STYLE', 'CAVALRYTWILL', 'Cavalry Twill', 20, 'seed', now()),
  (1, 'WEAVE_STYLE', 'HBT', 'HBT', 30, 'seed', now()),
  (1, 'WEAVE_STYLE', 'LONGFLOAT', 'Long Float', 40, 'seed', now()),
  (1, 'WEAVE_STYLE', 'MATT', 'Matt', 50, 'seed', now()),
  (1, 'WEAVE_STYLE', 'MEDIUMFLOAT', 'Medium Float', 60, 'seed', now()),
  (1, 'WEAVE_STYLE', 'OXFORD', 'Oxford', 70, 'seed', now()),
  (1, 'WEAVE_STYLE', 'PLAIN', 'Plain', 80, 'seed', now()),
  (1, 'WEAVE_STYLE', 'RIBSTOP', 'Ribstop', 90, 'seed', now()),
  (1, 'WEAVE_STYLE', 'STWILL', 'S Twill', 100, 'seed', now()),
  (1, 'WEAVE_STYLE', 'SATIN', 'Satin', 110, 'seed', now()),
  (1, 'WEAVE_STYLE', 'SHORTFLOAT', 'Short Float', 120, 'seed', now()),
  (1, 'WEAVE_STYLE', 'ZTWILL', 'Z Twill', 130, 'seed', now()),
  (1, 'WEAVE_STYLE', 'ZIGZAGTWILL', 'Zig Zag Twill', 140, 'seed', now());

-- FABRIC_TYPE: 20 values
INSERT INTO fab_attributes (organization_id, attribute_type, code, name, display_order, created_by, created_at) VALUES
  (1, 'FABRIC_TYPE', 'GREIGEINDIGO', 'Greige Indigo Denim', 10, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGEINDI2', 'Greige Indigo Denim Spandex', 20, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGESOLIDD', 'Greige Solid Dyed', 30, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGESOLI2', 'Greige Solid Dyed Lungi', 40, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGESOLI3', 'Greige Solid Dyed Spandex', 50, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGEYARNDY', 'Greige Yarn Dyed', 60, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGEYARN2', 'Greige Yarn Dyed Spandex', 70, 'seed', now()),
  (1, 'FABRIC_TYPE', 'INDIGODENIM', 'Indigo Denim', 80, 'seed', now()),
  (1, 'FABRIC_TYPE', 'INDIGODENIMS', 'Indigo Denim Spandex', 90, 'seed', now()),
  (1, 'FABRIC_TYPE', 'SOLIDDYED', 'Solid Dyed', 100, 'seed', now()),
  (1, 'FABRIC_TYPE', 'SOLIDDYEDPRI', 'Solid Dyed Print', 110, 'seed', now()),
  (1, 'FABRIC_TYPE', 'SOLIDDYEDP2', 'Solid Dyed Print Spandex', 120, 'seed', now()),
  (1, 'FABRIC_TYPE', 'SOLIDDYEDSPA', 'Solid Dyed Spandex', 130, 'seed', now()),
  (1, 'FABRIC_TYPE', 'YARNDYED', 'Yarn Dyed', 140, 'seed', now()),
  (1, 'FABRIC_TYPE', 'YARNDYEDLUNG', 'Yarn Dyed Lungi_Greige', 150, 'seed', now()),
  (1, 'FABRIC_TYPE', 'YARNDYEDPRIN', 'Yarn Dyed Print', 160, 'seed', now()),
  (1, 'FABRIC_TYPE', 'YARNDYEDPR2', 'Yarn Dyed Print Spandex', 170, 'seed', now()),
  (1, 'FABRIC_TYPE', 'YARNDYEDSPAN', 'Yarn Dyed Spandex', 180, 'seed', now()),
  (1, 'FABRIC_TYPE', 'GREIGESOLI4', 'Greige Solid Dyed (LUNGI)', 190, 'seed', now()),
  (1, 'FABRIC_TYPE', 'YARNDYEDLU2', 'Yarn Dyed LUNGI (Greige)', 200, 'seed', now());

-- FINISH_TYPE: 16 values
INSERT INTO fab_attributes (organization_id, attribute_type, code, name, display_order, created_by, created_at) VALUES
  (1, 'FINISH_TYPE', 'AEROFINISH', 'Aero Finish', 10, 'seed', now()),
  (1, 'FINISH_TYPE', 'BOTHSIDEPEAC', 'Both Side Peach', 20, 'seed', now()),
  (1, 'FINISH_TYPE', 'BRUSH', 'Brush', 30, 'seed', now()),
  (1, 'FINISH_TYPE', 'CARBONPEACHF', 'Carbon peach Finish', 40, 'seed', now()),
  (1, 'FINISH_TYPE', 'ETIWRINKLEFR', 'ETI/ Wrinkle free Finish', 50, 'seed', now()),
  (1, 'FINISH_TYPE', 'HEAVYPEACHFI', 'Heavy peach Finish', 60, 'seed', now()),
  (1, 'FINISH_TYPE', 'PAPERTOUCHHA', 'Paper touch/Hard Finish', 70, 'seed', now()),
  (1, 'FINISH_TYPE', 'PEACHFINISH', 'Peach Finish', 80, 'seed', now()),
  (1, 'FINISH_TYPE', 'RASINFINISH', 'Rasin Finish', 90, 'seed', now()),
  (1, 'FINISH_TYPE', 'SILKYFINISH', 'Silky Finish', 100, 'seed', now()),
  (1, 'FINISH_TYPE', 'SOFTFINISH', 'Soft Finish', 110, 'seed', now()),
  (1, 'FINISH_TYPE', 'WATERREPELLE', 'Water repellent Finish', 120, 'seed', now()),
  (1, 'FINISH_TYPE', 'CHEMICALORWE', 'Chemical or wet finishes', 130, 'seed', now()),
  (1, 'FINISH_TYPE', 'FUNCTIONALFI', 'Functional finishes', 140, 'seed', now()),
  (1, 'FINISH_TYPE', 'MECHANICALFI', 'Mechanical finishes', 150, 'seed', now()),
  (1, 'FINISH_TYPE', 'PERFORMANCEF', 'performance finishes', 160, 'seed', now());

-- SELVEDGE: 10 values
INSERT INTO fab_attributes (organization_id, attribute_type, code, name, display_order, created_by, created_at) VALUES
  (1, 'SELVEDGE', '10MM10MM', '10 mm + 10 mm', 10, 'seed', now()),
  (1, 'SELVEDGE', '12MM12MM', '12 mm + 12 mm', 20, 'seed', now()),
  (1, 'SELVEDGE', '15MM15MM', '15 mm + 15 mm', 30, 'seed', now()),
  (1, 'SELVEDGE', '20MM20MM', '20 mm + 20 mm', 40, 'seed', now()),
  (1, 'SELVEDGE', '22MM22MM', '22 mm + 22 mm', 50, 'seed', now()),
  (1, 'SELVEDGE', '25MM25MM', '25 mm + 25 mm', 60, 'seed', now()),
  (1, 'SELVEDGE', '5MM5MM', '5 mm + 5 mm', 70, 'seed', now()),
  (1, 'SELVEDGE', '6MM6MM', '6 mm + 6 mm', 80, 'seed', now()),
  (1, 'SELVEDGE', '8MM8MM', '8 mm + 8 mm', 90, 'seed', now()),
  (1, 'SELVEDGE', 'ASBELOW', 'As Below', 100, 'seed', now());

-- LIGHT_SOURCE: 7 values
INSERT INTO fab_attributes (organization_id, attribute_type, code, name, display_order, created_by, created_at) VALUES
  (1, 'LIGHT_SOURCE', 'CWF', 'CWF', 10, 'seed', now()),
  (1, 'LIGHT_SOURCE', 'D65', 'D65', 20, 'seed', now()),
  (1, 'LIGHT_SOURCE', 'FILAMENT', 'Filament', 30, 'seed', now()),
  (1, 'LIGHT_SOURCE', 'TL83', 'TL83', 40, 'seed', now()),
  (1, 'LIGHT_SOURCE', 'TL84', 'TL84', 50, 'seed', now()),
  (1, 'LIGHT_SOURCE', 'U30LED', 'U30LED', 60, 'seed', now()),
  (1, 'LIGHT_SOURCE', 'UV', 'UV', 70, 'seed', now());
