package com.asg.fabricerp.supply;

import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;

import java.math.BigDecimal;

/**
 * The share of an import's costs - freight, insurance, bank and C&amp;F charges recorded against its
 * PI and LC - that an MRR line carries into stock. Implemented by the commercial module, which
 * knows the PI and LC; stores ask it at posting and need nothing else about commercial paper.
 */
public interface LandedCostProvider {

    /**
     * The costs (in taka) {@code mrrLine} takes on: none for a local purchase.
     *
     * @param mrrLine an MRR line, drawn on a purchase order line
     */
    BigDecimal allocatedCost(BusinessDocumentColorLine mrrLine);
}
