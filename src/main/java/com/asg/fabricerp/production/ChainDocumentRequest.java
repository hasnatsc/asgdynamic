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
 * @param groups production order only: the greige allowance the planner set per fabric line,
 *               keyed by the Booking fabric line it comes from
 */
public record ChainDocumentRequest(Long id, LocalDate documentDate, LocalDate requiredDate, Long warehouseId,
                                   Long vendorId, ProcessKind processKind, String referenceNo, Long garmentsId,
                                   String garmentsAddress, String vehicleNo, String driverName, String remarks,
                                   List<Line> lines, List<GroupSetting> groups) {

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
