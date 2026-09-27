-- =============================================================================================
-- Booking: each colour's depth - Light, Medium or Dark - keyed beside its name in the colour
-- breakdown, and carried down the production chain with the colour's references.
--
-- Not the existing "shade" column: that is the shade a dyed lot actually came out as (B2 ...),
-- recorded on receipt and part of a lot's identity in stock. This is what the buyer booked.
-- =============================================================================================

ALTER TABLE gbl_business_document_color_lines
    ADD COLUMN color_shade VARCHAR(10),
    ADD CONSTRAINT ck_gbdcl_color_shade CHECK (color_shade IN ('Light', 'Medium', 'Dark'));
