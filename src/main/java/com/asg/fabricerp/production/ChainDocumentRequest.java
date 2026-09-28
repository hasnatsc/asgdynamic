package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.ProcessKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a production chain editor sends. The server builds the document from it: every line is
 * taken from the parent line it names, so buyer, team, fabric, colour and route are inherited,
 * never typed - an order cannot drift to another buyer or team mid-chain.
 *
 * @param groups       production order only: the greige allowance the planner set per fabric line,
 *                     keyed by the Booking fabric line it comes from
 * @param requirements production order only: what the buyer wants with the goods; null leaves them as they are
 * @param preDeliveries production order only: its pre-delivery schedule, replacing the saved one; null leaves it
 */
public record ChainDocumentRequest(Long id, LocalDate documentDate, LocalDate requiredDate, Long warehouseId,
                                   Long vendorId, ProcessKind processKind, String referenceNo, Long garmentsId,
                                   String garmentsAddress, String vehicleNo, String driverName, String remarks,
                                   List<Line> lines, List<GroupSetting> groups, Requirements requirements,
                                   List<PreDelivery> preDeliveries) {

    /** Without production order requirements - every step but the production order. */
    public ChainDocumentRequest(Long id, LocalDate documentDate, LocalDate requiredDate, Long warehouseId,
                                Long vendorId, ProcessKind processKind, String referenceNo, Long garmentsId,
                                String garmentsAddress, String vehicleNo, String driverName, String remarks,
                                List<Line> lines, List<GroupSetting> groups) {
        this(id, documentDate, requiredDate, warehouseId, vendorId, processKind, referenceNo, garmentsId,
            garmentsAddress, vehicleNo, driverName, remarks, lines, groups, null, null);
    }

    /**
     * The production order's checkboxes, as the legacy BPO screen had them. Price in metre is not
     * here: it is the booking's, because it says what unit the quantities are in.
     */
    /**
     * One planned delivery on a production order: its type, date, colour (the booking colour line
     * the order's line is drawn from, as {@code sourceId} on a line), quantity and serial number.
     */
    public record PreDelivery(Long deliveryTypeId, LocalDate deliveryDate, Long sourceId, BigDecimal quantity,
                              Integer serialNo) { }

    public record Requirements(boolean inHouseTestReport, boolean inspectionReport, boolean dyeLot,
                               boolean testFabrics, boolean blanket, boolean headCutting, boolean packingList) { }

    /**
     * One line, drawn on one parent line.
     *
     * @param sourceKind COLOUR (a parent colour line) or GROUP (a production order fabric line, for
     *                   construction-keyed weaving)
     * @param lotId      Greige issue and delivery order: the stock lot picked
     */
    public record Line(String sourceKind, Long sourceId, BigDecimal quantity, Long lotId, Integer rolls,
                       String dyeLot, String shade, String grade, LocalDate deliveryDate, String remarks,
                       Long revisedFromLineId) { }

    public record GroupSetting(Long sourceGroupId, BigDecimal greigeAllowancePct) { }

    public List<Line> lines() {
        return lines == null ? List.of() : lines;
    }

    public List<GroupSetting> groups() {
        return groups == null ? List.of() : groups;
    }
}
