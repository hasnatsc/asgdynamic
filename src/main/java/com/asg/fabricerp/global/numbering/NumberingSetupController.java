package com.asg.fabricerp.global.numbering;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administration → Document numbering. */
@Controller
public class NumberingSetupController {

    private static final String VIEW = "hasAuthority('SCREEN_NUMBERING_VIEW')";
    private static final String AMEND = "hasAuthority('SCREEN_NUMBERING_AMEND')";

    public record FiscalYearRequest(Integer startMonth) { }

    private final NumberingSetupService service;

    public NumberingSetupController(NumberingSetupService service) {
        this.service = service;
    }

    @GetMapping("/setup/numbering")
    @PreAuthorize(VIEW)
    public String page(Model model) {
        model.addAttribute("title", "Document numbering");
        model.addAttribute("resetPolicies", ResetPolicy.values());
        model.addAttribute("counterScopes", CounterScope.values());
        model.addAttribute("content", "setup/numbering :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/numbering")
    @ResponseBody
    @PreAuthorize(VIEW)
    public Map<String, Object> overview() {
        return service.overview();
    }

    @PutMapping("/api/setup/numbering/series/{seriesCode}")
    @ResponseBody
    @PreAuthorize(AMEND)
    public Map<String, Object> update(@PathVariable String seriesCode,
                                      @RequestBody NumberingSetupService.SchemeRequest request) {
        return service.update(seriesCode, request);
    }

    @PutMapping("/api/setup/numbering/fiscal-year")
    @ResponseBody
    @PreAuthorize(AMEND)
    public Map<String, Object> updateFiscalYear(@RequestBody FiscalYearRequest request) {
        if (request.startMonth() == null) throw new IllegalArgumentException("Choose the month the financial year starts in.");
        return service.updateFiscalYear(request.startMonth());
    }
}
