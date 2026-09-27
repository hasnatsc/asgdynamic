package com.asg.fabricerp.supply;

import com.asg.fabricerp.global.documents.PurchaseType;
import com.asg.fabricerp.global.documents.RequisitionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a purchase or store editor sends. A line either names the parent line it is raised against
 * ({@code sourceId}) - and then the item, unit and price come from that line, never from the
 * request - or, where the step allows it, names an item (or a fabric lot) directly.
 *
 * @param warehouseId   the store: the one asking (SR), receiving (MRR, direct receive), issuing
 *                      (material issue, transfers) or adjusted; for a transfer, the one it leaves
 * @param toWarehouseId transfers: the store the stock goes to
 * @param supplierId    purchase order: the supplier; an MRR and a return inherit it
 */
public record SupplyDocumentRequest(Long id, LocalDate documentDate, LocalDate requiredDate, Long warehouseId,
                                    Long toWarehouseId, Long supplierId, PurchaseType purchaseType,
                                    RequisitionType requisitionType, String department, String currencyCode,
                                    BigDecimal exchangeRate, Integer leadTimeDays, String referenceNo, String invoiceNo,
                                    String vehicleNo, String remarks, List<Line> lines) {

    /**
     * One line.
     *
     * @param sourceId       the parent line it draws on, or null for a direct line
     * @param itemId         direct lines: the item
     * @param lotId          fabric transfer issue: the fabric lot moved
     * @param rate           purchase order and direct receive: the unit price; adjustments that add stock:
     *                       the unit cost (blank for the store's current average)
     * @param stockDirection stock adjustment: IN or OUT
     * @param fabric         a fabric item's construction and finish, as bought or received
     */
    public record Line(Long sourceId, Long itemId, Long lotId, BigDecimal quantity, BigDecimal rate, Integer rolls,
                       Long brandId, Long modelId, String specification, String originCountry, String conditionNote,
                       String stockDirection, String remarks, FabricDetail fabric) { }

    /** The legacy MRR's fabric fields, kept on the line's fabric specification. */
    public record FabricDetail(String construction, String composition, String weaveType, String finishType,
                               BigDecimal finishWidth, BigDecimal gsm, String warpCount, String weftCount,
                               BigDecimal epi, BigDecimal ppi) { }

    public List<Line> lines() {
        return lines == null ? List.of() : lines;
    }
}
