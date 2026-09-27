package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.inventory.item.ItemType;
import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Item stock (balances, the item ledger, the monthly stock report) and the inventory periods that
 * close a month's stock to further postings.
 */
@Controller
public class ItemStockController {

    private final ItemStockQueries stock;
    private final InventoryPeriodService periods;
    private final WarehouseRepository warehouses;
    private final OrgContext context;

    public ItemStockController(ItemStockQueries stock, InventoryPeriodService periods, WarehouseRepository warehouses,
                               OrgContext context) {
        this.stock = stock;
        this.periods = periods;
        this.warehouses = warehouses;
        this.context = context;
    }

    // ------------------------------------------------------------------------------ item stock

    @GetMapping("/stock/items")
    @PreAuthorize("hasAuthority('SCREEN_ITEM_STOCK_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Item stock");
        model.addAttribute("stores", warehouses.lookup(context.requireOrganizationId()));
        model.addAttribute("itemTypes", Arrays.stream(ItemType.values()).filter(ItemType::isStockItem)
            .map(t -> Map.of("value", t.name(), "label", t.label())).toList());
        model.addAttribute("month", YearMonth.now().toString());
        model.addAttribute("canAdjust", AuthorityChecks.holds("SCREEN_SA_CREATE"));
        model.addAttribute("canReceive", AuthorityChecks.holds("SCREEN_MR_CREATE"));
        model.addAttribute("content", "supply/stock :: content");
        return "layout/main";
    }

    @GetMapping("/api/stock/items")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_STOCK_VIEW')")
    public Map<String, Object> balances(@RequestParam(required = false) Long warehouseId,
                                        @RequestParam(required = false) ItemType itemType,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "false") boolean withZero,
                                        @RequestParam(defaultValue = "false") boolean lowOnly,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return stock.balances(warehouseId, itemType, q, withZero, lowOnly, page, Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/api/stock/items/{itemId:\\d+}/ledger")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_STOCK_VIEW')")
    public Map<String, Object> ledger(@PathVariable Long itemId, @RequestParam(required = false) Long warehouseId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stock.ledger(itemId, warehouseId, from, to);
    }

    @GetMapping("/api/stock/items/monthly")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_ITEM_STOCK_VIEW')")
    public Map<String, Object> monthly(@RequestParam(required = false) String month,
                                       @RequestParam(required = false) Long warehouseId,
                                       @RequestParam(required = false) ItemType itemType) {
        return stock.monthly(month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month), warehouseId, itemType);
    }

    // --------------------------------------------------------------------------------- periods

    @GetMapping("/stock/periods")
    @PreAuthorize("hasAuthority('SCREEN_INV_PERIOD_VIEW')")
    public String periodsPage(Model model) {
        model.addAttribute("title", "Inventory periods");
        model.addAttribute("year", LocalDate.now().getYear());
        model.addAttribute("canClose", AuthorityChecks.holds("SCREEN_INV_PERIOD_AMEND"));
        model.addAttribute("canReopen", AuthorityChecks.holds("SCREEN_INV_PERIOD_APPROVE"));
        model.addAttribute("content", "supply/periods :: content");
        return "layout/main";
    }

    @GetMapping("/api/stock/periods")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_INV_PERIOD_VIEW')")
    public List<Map<String, Object>> periodYear(@RequestParam(required = false) Integer year) {
        return periods.year(year == null ? LocalDate.now().getYear() : year);
    }

    @PostMapping("/api/stock/periods/{month:\\d{4}-\\d{2}}/close")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_INV_PERIOD_AMEND')")
    public Map<String, Object> close(@PathVariable String month, @RequestParam(required = false) String remarks) {
        periods.close(YearMonth.parse(month), remarks);
        return Map.of("month", month, "status", "CLOSED");
    }

    /** Reopening undoes a close someone else signed off - an approver's decision, with a reason. */
    @PostMapping("/api/stock/periods/{month:\\d{4}-\\d{2}}/reopen")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_INV_PERIOD_APPROVE')")
    public Map<String, Object> reopen(@PathVariable String month, @RequestParam(required = false) String reason) {
        periods.reopen(YearMonth.parse(month), reason);
        return Map.of("month", month, "status", "OPEN");
    }
}
