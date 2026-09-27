package com.asg.fabricerp.commercial;

import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.global.documents.PurchaseType;
import com.asg.fabricerp.supply.LandedCostProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An import's costs, spread over what is received - the legacy GRN (Import)'s "allocated expense".
 *
 * <p>The costs recorded against the import PI and every live import LC opened on it (freight,
 * insurance, bank and C&amp;F charges, in taka) are shared by value: an MRR line takes the part of
 * them its goods are of the PI's value. Only costs recorded by the time the MRR is posted are
 * included - the MRR shows the share it was valued at, so a later cost is visibly not in it.
 */
@Component
public class ImportLandedCosts implements LandedCostProvider {

    private final NamedParameterJdbcTemplate jdbc;

    public ImportLandedCosts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal allocatedCost(BusinessDocumentColorLine mrrLine) {
        BusinessDocumentColorLine poLine = mrrLine.getSourceColorLine();
        if (poLine == null) return BigDecimal.ZERO;
        BusinessDocument po = poLine.getLineGroup().getDocument();
        BusinessDocumentColorLine piLine = poLine.getSourceColorLine();
        if (po.getPurchaseType() != PurchaseType.IMPORT || piLine == null
            || piLine.getLineGroup().getDocument().getDocumentType() != DocumentType.IMPORT_PROFORMA_INVOICE) {
            return BigDecimal.ZERO;
        }
        BusinessDocument pi = piLine.getLineGroup().getDocument();
        BigDecimal costs = jdbc.queryForObject("""
            SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e
            WHERE e.kind = 'COST' AND e.document_id IN (
                SELECT :pi
                UNION
                SELECT DISTINCT d.id FROM gbl_business_documents d
                JOIN gbl_business_document_line_groups g ON g.document_id = d.id
                JOIN gbl_business_document_color_lines l ON l.line_group_id = g.id
                JOIN gbl_business_document_color_lines pl ON pl.id = l.source_color_line_id
                JOIN gbl_business_document_line_groups pg ON pg.id = pl.line_group_id
                WHERE pg.document_id = :pi AND d.document_type = 'IMPORT_LETTER_OF_CREDIT'
                  AND d.deleted = FALSE AND d.status <> 'CANCELLED')
            """, new MapSqlParameterSource("pi", pi.getId()), BigDecimal.class);
        BigDecimal piValue = pi.getSubtotalAmount().multiply(rate(pi));
        if (costs.signum() <= 0 || piValue.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal received = mrrLine.getQuantity().multiply(poLine.getRate()).multiply(rate(po));
        return costs.multiply(received).divide(piValue, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BusinessDocument d) {
        return d.getExchangeRate() == null ? BigDecimal.ONE : d.getExchangeRate();
    }
}
