package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.production.FabricStockService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.asg.fabricerp.supply.SupplyDraws.lineName;

/**
 * Turns a purchase or store document's lines into stock moves - the one place that decides which
 * ledger each step writes and what it is valued at:
 * <ul>
 *   <li>MRR: in, at the order's price times its rate to taka - for an import, plus its duties and its
 *       share of the PI's and LC's costs (its landed cost);</li>
 *   <li>direct receive and an adjustment that adds: in, at the price given, else the store's
 *       average, else the item's cost price;</li>
 *   <li>issue, purchase return, transfer issue and an adjustment that takes away: out, at the
 *       store's average;</li>
 *   <li>transfer receive: in, at exactly what its transfer issue took out;</li>
 *   <li>fabric transfers: the lot out of one store and into the other, unchanged.</li>
 * </ul>
 * Kept apart from {@link SupplyPostingService} because an approval (a stock adjustment's) writes
 * moves too, and the approval engine must not depend on what depends on it.
 */
@Component
public class SupplyStockWriter {

    private final ItemStockService stock;
    private final FabricStockService fabric;
    private final InventoryPeriodService periods;
    private final OrgContext context;
    private final ObjectProvider<LandedCostProvider> landedCosts;

    public SupplyStockWriter(ItemStockService stock, FabricStockService fabric, InventoryPeriodService periods, OrgContext context,
                             ObjectProvider<LandedCostProvider> landedCosts) {
        this.stock = stock;
        this.fabric = fabric;
        this.periods = periods;
        this.context = context;
        this.landedCosts = landedCosts;
    }

    public void write(SupplyStep step, BusinessDocument doc) {
        periods.requireOpen(doc.getDocumentDate());
        Long orgId = context.requireOrganizationId();
        String user = context.username();
        LocalDate date = doc.getDocumentDate();
        if (doc.getWarehouse() == null) throw new IllegalStateException("Choose the store on %s before posting it".formatted(doc.getDocumentNo()));
        Long store = doc.getWarehouse().getId();
        Long toStore = doc.getToWarehouse() == null ? null : doc.getToWarehouse().getId();
        for (BusinessDocumentLineGroup g : doc.getLineGroups()) {
            for (BusinessDocumentColorLine line : g.getColorLines()) {
                BigDecimal qty = line.getQuantity();
                if (qty.signum() <= 0) continue;
                String what = "%s, %s".formatted(doc.getDocumentNo(), lineName(line));
                InventoryItem item = g.getItem();
                switch (step) {
                    case MRR -> stock.receive(orgId, move(store, item, qty, "RECEIPT", doc, line, date, what),
                        landedUnitCost(doc, line), user);
                    case MR -> stock.receive(orgId, move(store, item, qty, "DIRECT_RECEIVE", doc, line, date, what),
                        costIn(store, item, line.getRate()), user);
                    case PRT -> stock.issue(orgId, move(store, item, qty, "PURCHASE_RETURN", doc, line, date, what), user);
                    case MI -> stock.issue(orgId, move(store, item, qty, "ISSUE", doc, line, date, what), user);
                    case TI -> stock.issue(orgId, move(store, item, qty, "TRANSFER_OUT", doc, line, date, what), user);
                    case TRC -> {
                        BigDecimal cost = stock.postedUnitCost(line.getSourceColorLine().getId());
                        if (cost == null) throw new IllegalStateException(what + ": its transfer issue has not been posted");
                        stock.receive(orgId, move(toStore, item, qty, "TRANSFER_IN", doc, line, date, what), cost, user);
                    }
                    case SA -> {
                        if ("IN".equals(line.getStockDirection())) {
                            stock.receive(orgId, move(store, item, qty, "ADJUST_IN", doc, line, date, what),
                                costIn(store, item, line.getRate()), user);
                        } else {
                            stock.issue(orgId, move(store, item, qty, "ADJUST_OUT", doc, line, date, what), user);
                        }
                    }
                    case FTI -> fabric.issue(orgId, fabricMove(store, line, g, "TRANSFER_OUT", doc), what, user);
                    case FTR -> fabric.receive(orgId, fabricMove(toStore, line, g, "TRANSFER_IN", doc), user);
                    default -> throw new IllegalStateException(step.label() + " has no stock posting");
                }
            }
        }
    }

    private static ItemStockService.Move move(Long store, InventoryItem item, BigDecimal qty, String type, BusinessDocument doc,
                                              BusinessDocumentColorLine line, LocalDate date, String what) {
        if (item == null) throw new IllegalStateException(what + " names no item");
        return new ItemStockService.Move(store, item.getId(), qty, type, doc.getId(), line.getId(), date, what);
    }

    private static FabricStockService.Move fabricMove(Long store, BusinessDocumentColorLine line, BusinessDocumentLineGroup g,
                                                      String type, BusinessDocument doc) {
        if (line.getFabricLotId() == null) throw new IllegalStateException("A fabric transfer line names no lot");
        return new FabricStockService.Move(store, line.getFabricLotId(), line.getQuantity(),
            line.getRolls() == null ? 0 : line.getRolls(), g.getUom() == null ? null : g.getUom().getId(), type, doc.getId(), line.getId());
    }

    /**
     * What a received unit cost to land: the order's price in taka, plus - for an import - the
     * line's duties and its share of the PI's and LC's costs, spread over the quantity. The share
     * is fixed on the line when it is posted, so the MRR shows what it was valued at.
     */
    private BigDecimal landedUnitCost(BusinessDocument doc, BusinessDocumentColorLine line) {
        LandedCostProvider provider = landedCosts.getIfAvailable();
        line.setAllocatedCost(provider == null ? BigDecimal.ZERO : provider.allocatedCost(line).setScale(6, java.math.RoundingMode.HALF_UP));
        BigDecimal extras = line.getCustomsDuty().add(line.getSupplementaryDuty()).add(line.getAllocatedCost());
        return line.getRate().multiply(fx(doc))
            .add(extras.divide(line.getQuantity(), ItemStockService.COST_SCALE, java.math.RoundingMode.HALF_UP));
    }

    /** The cost stock comes in at without a purchase: the price given, else the store's average, else the item's cost price. */
    private BigDecimal costIn(Long store, InventoryItem item, BigDecimal given) {
        if (given != null && given.signum() > 0) return given;
        BigDecimal average = stock.balance(store, item.getId()).averageCost();
        if (average.signum() > 0) return average;
        return item.getCostPrice() == null ? BigDecimal.ZERO : item.getCostPrice();
    }

    private static BigDecimal fx(BusinessDocument doc) {
        return doc.getExchangeRate() == null ? BigDecimal.ONE : doc.getExchangeRate();
    }
}
