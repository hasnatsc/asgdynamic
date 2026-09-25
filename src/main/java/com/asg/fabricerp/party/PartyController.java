package com.asg.fabricerp.party;

import com.asg.fabricerp.common.LookupPage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The counterparty picker - read-only, as in asfl-erp. A booking picks a customer, a purchase
 * order a supplier, an LC a bank: one endpoint parameterised by role.
 *
 * <p>{@code isAuthenticated()} rather than a screen grant: this serves every screen with a party
 * field, and keying it to one screen would make the Booking customer dropdown depend on a grant
 * for a different screen. The role is required on purpose - a screen that does not know which
 * role it needs is a screen that will accept a bank as a buyer.
 */
@RestController
@RequestMapping("/api/parties")
@PreAuthorize("isAuthenticated()")
public class PartyController {

    private final PartyService parties;

    public PartyController(PartyService parties) {
        this.parties = parties;
    }

    /** e.g. {@code /api/parties/directory?role=CUSTOMER&q=acme} */
    @GetMapping("/directory")
    public PartyService.DirectoryPage directory(@RequestParam PartyRoleType role,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "true") boolean activeOnly,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return parties.directory(role, q, activeOnly, page, size);
    }

    /**
     * The directory for App.RemoteSelect: {@code /api/parties/lookup?role=BRAND&q=zara&page=2}
     * answers {@code {results, pagination}}; {@code ?id=7} labels a saved value. e.g.
     * {@code <select data-remote="/api/parties/lookup?role=CUSTOMER">}.
     */
    @GetMapping("/lookup")
    public LookupPage<LookupPage.Option> lookup(@RequestParam(required = false) PartyRoleType role,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "true") boolean activeOnly,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) Long id) {
        if (id != null) return parties.option(id);
        if (role == null) throw new IllegalArgumentException("Say which role the picker lists, e.g. role=CUSTOMER.");
        return parties.lookup(role, q, activeOnly, page, size);
    }

    /**
     * One party, for rendering a document that already names it. Retired parties resolve here -
     * a booking whose buyer shows as a bare id because that buyer was retired is a support call.
     */
    @GetMapping("/{id}")
    public Map<String, Object> find(@PathVariable Long id) {
        Party party = parties.require(id);
        return Map.of("id", party.getId(), "code", party.getCode(), "name", party.getName(),
            "active", party.getActive(), "roles", party.activeRoles());
    }
}
