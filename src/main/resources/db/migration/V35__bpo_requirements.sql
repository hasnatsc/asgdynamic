-- =============================================================================================
-- Production order requirements: what the buyer wants with the goods, ticked on the order as the
-- legacy BPO screen did - in-house test report, inspection report, dye lot, test fabrics, blanket,
-- head cutting, packing list. (Price in metre is already on the header, taken from the booking.)
-- =============================================================================================

ALTER TABLE gbl_business_documents
    ADD COLUMN in_house_test_report  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN inspection_report     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dye_lot_required      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN test_fabrics          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blanket               BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN head_cutting          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN packing_list          BOOLEAN NOT NULL DEFAULT FALSE;
