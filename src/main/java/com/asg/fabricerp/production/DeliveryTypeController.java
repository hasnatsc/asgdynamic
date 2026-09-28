package com.asg.fabricerp.production;

import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Master data -> Delivery types. The list is also read by the production order's pre-delivery
 * schedule, so whoever may view production orders may read it.
 */
@Controller
public class DeliveryTypeController {

    private final DeliveryTypeService types;

    public DeliveryTypeController(DeliveryTypeService types) {
        this.types = types;
    }

    @GetMapping("/setup/delivery-types")
    @PreAuthorize("hasAuthority('SCREEN_DELIVERY_TYPE_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Delivery types");
        model.addAttribute("canCreate", AuthorityChecks.holds("SCREEN_DELIVERY_TYPE_CREATE"));
        model.addAttribute("canAmend", AuthorityChecks.holds("SCREEN_DELIVERY_TYPE_AMEND"));
        model.addAttribute("canDelete", AuthorityChecks.holds("SCREEN_DELIVERY_TYPE_DELETE"));
        model.addAttribute("content", "production/delivery-types :: content");
        return "layout/main";
    }

    /** Every type, retired ones included (the screen shows them; a schedule offers only active ones). */
    @GetMapping("/api/delivery-types")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DELIVERY_TYPE_VIEW') or @chainAccess.can('bpo', 'VIEW')")
    public List<Map<String, Object>> list() {
        return types.list().stream().map(DeliveryTypeController::row).toList();
    }

    @PostMapping("/api/delivery-types")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DELIVERY_TYPE_CREATE') or hasAuthority('SCREEN_DELIVERY_TYPE_AMEND')")
    public Map<String, Object> save(@RequestParam(required = false) Long id, @RequestBody DeliveryTypeService.Request request) {
        AuthorityChecks.require(id == null ? "SCREEN_DELIVERY_TYPE_CREATE" : "SCREEN_DELIVERY_TYPE_AMEND");
        return row(types.save(id, request));
    }

    @DeleteMapping("/api/delivery-types/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_DELIVERY_TYPE_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        return Map.of("outcome", types.delete(id));
    }

    private static Map<String, Object> row(DeliveryType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("sortOrder", t.getSortOrder());
        m.put("active", Boolean.TRUE.equals(t.getActive()));
        return m;
    }
}
