package com.asg.fabricerp.commercial;

import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The commercial register and commercial setup (document names, cost heads). */
@Controller
public class CommercialScreensController {

    private final CommercialRegisterQueries register;
    private final CommercialSetupService setup;

    public CommercialScreensController(CommercialRegisterQueries register, CommercialSetupService setup) {
        this.register = register;
        this.setup = setup;
    }

    // --------------------------------------------------------------------------------- register

    @GetMapping("/commercial/register")
    @PreAuthorize("hasAuthority('SCREEN_COM_REGISTER_VIEW')")
    public String registerPage(Model model) {
        model.addAttribute("title", "Commercial register");
        model.addAttribute("content", "commercial/register :: content");
        return "layout/main";
    }

    @GetMapping("/api/commercial/register/summary")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_REGISTER_VIEW')")
    public Map<String, Object> summary() {
        return register.summary();
    }

    @GetMapping("/api/commercial/register/{list:export-pi|export-lc|export-ci|import-pi|import-lc}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_REGISTER_VIEW')")
    public List<Map<String, Object>> list(@PathVariable String list, @RequestParam(required = false) String q,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return switch (list) {
            case "export-pi" -> register.exportPis(q, from, to);
            case "export-lc" -> register.exportLcs(q, from, to);
            case "export-ci" -> register.exportCis(q, from, to);
            case "import-pi" -> register.importPis(q, from, to);
            default -> register.importLcs(q, from, to);
        };
    }

    // ------------------------------------------------------------------------------------ setup

    @GetMapping("/commercial/setup")
    @PreAuthorize("hasAuthority('SCREEN_COM_SETUP_VIEW')")
    public String setupPage(Model model) {
        model.addAttribute("title", "Commercial setup");
        model.addAttribute("canEdit", AuthorityChecks.holds("SCREEN_COM_SETUP_CREATE") || AuthorityChecks.holds("SCREEN_COM_SETUP_AMEND"));
        model.addAttribute("canDelete", AuthorityChecks.holds("SCREEN_COM_SETUP_DELETE"));
        model.addAttribute("content", "commercial/setup :: content");
        return "layout/main";
    }

    @GetMapping("/api/commercial/setup")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_SETUP_VIEW')")
    public Map<String, Object> setupData() {
        return Map.of(
            "documentNames", setup.documentNames().stream().map(n -> Map.<String, Object>of("id", n.getId(), "code", n.getCode(),
                "name", n.getName(), "docKind", n.getDocKind(), "sortOrder", n.getSortOrder(), "active", n.getActive())).toList(),
            "costHeads", setup.costHeads().stream().map(h -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", h.getId());
                m.put("code", h.getCode());
                m.put("name", h.getName());
                m.put("docKind", h.getDocKind());
                m.put("accountCode", h.getAccountCode());
                m.put("active", h.getActive());
                return m;
            }).toList());
    }

    @PostMapping("/api/commercial/setup/document-names")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_SETUP_CREATE') or hasAuthority('SCREEN_COM_SETUP_AMEND')")
    public Map<String, Object> saveName(@RequestParam(required = false) Long id, @RequestBody CommercialSetupService.NameRequest r) {
        AuthorityChecks.require(id == null ? "SCREEN_COM_SETUP_CREATE" : "SCREEN_COM_SETUP_AMEND");
        return Map.of("id", setup.saveName(id, r).getId());
    }

    @PostMapping("/api/commercial/setup/cost-heads")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_SETUP_CREATE') or hasAuthority('SCREEN_COM_SETUP_AMEND')")
    public Map<String, Object> saveCostHead(@RequestParam(required = false) Long id, @RequestBody CommercialSetupService.CostHeadRequest r) {
        AuthorityChecks.require(id == null ? "SCREEN_COM_SETUP_CREATE" : "SCREEN_COM_SETUP_AMEND");
        return Map.of("id", setup.saveCostHead(id, r).getId());
    }

    @DeleteMapping("/api/commercial/setup/document-names/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_SETUP_DELETE')")
    public Map<String, Object> deleteName(@PathVariable Long id) {
        return Map.of("outcome", setup.deleteName(id));
    }

    @DeleteMapping("/api/commercial/setup/cost-heads/{id:\\d+}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_COM_SETUP_DELETE')")
    public Map<String, Object> deleteCostHead(@PathVariable Long id) {
        return Map.of("outcome", setup.deleteCostHead(id));
    }
}
