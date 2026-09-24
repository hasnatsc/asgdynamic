package com.asg.fabricerp.fabric.setup;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Feeds every fabric dropdown from one endpoint.
 *
 * asgdynamic re-rendered the same list per screen under entity-prefixed field names
 * (so_dtlSet_weaveType, po_dtlSet_weaveType, grm_dtlSet_countries ...), duplicating both
 * the markup and the query. Every screen now binds to this.
 */
@RestController
@RequestMapping("/api/lookup/fabric")
@PreAuthorize("isAuthenticated()")
public class FabricLookupController {

    /** What a Select2/Tailwind combobox needs, and nothing more. */
    public record Option(Long id, String code, String text) {}

    private final FabricAttributeService service;

    public FabricLookupController(FabricAttributeService service) {
        this.service = service;
    }

    /** e.g. GET /api/lookup/fabric/weave-type */
    @GetMapping("/{slug}")
    public List<Option> options(@PathVariable String slug) {
        AttributeType type = AttributeType.fromSlug(slug);
        return service.lookup(type).stream()
            .map(a -> new Option(a.getId(), a.getCode(), a.getName()))
            .toList();
    }

    /** The list of lists, for building setup menus without hardcoding them. */
    @GetMapping
    public List<Option> types() {
        return java.util.Arrays.stream(AttributeType.values())
            .map(t -> new Option(null, t.slug(), t.label()))
            .toList();
    }
}
