-- Line-to-line linkage: the upstream line a line draws against (BPO line -> Booking line,
-- MRR line -> PO line, ...). Added after V1 rather than folded into it: V1 is treated as
-- already shipped and verified, so schema changes are additive migrations from here on,
-- the same discipline the rest of this project expects of anyone extending it.
--
-- FOREIGN KEY, not just an index: a source line that has already been drawn against must
-- not be deletable out from under the document that drew against it. ON DELETE RESTRICT
-- is the business-correct default here — losing the paper trail of what was allocated
-- against what is a worse outcome than a blocked delete.

ALTER TABLE gbl_business_document_lines
    ADD COLUMN source_line_id BIGINT;

ALTER TABLE gbl_business_document_lines
    ADD CONSTRAINT fk_gbdl_source_line FOREIGN KEY (source_line_id)
        REFERENCES gbl_business_document_lines (id) ON DELETE RESTRICT;

CREATE INDEX ix_gbdl_source_line ON gbl_business_document_lines (source_line_id)
    WHERE source_line_id IS NOT NULL;
