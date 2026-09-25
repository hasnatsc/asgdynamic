package com.asg.fabricerp.party;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Resolving parties and checking what they may be named as. Ported from asfl-erp's
 * {@code PartyServiceImpl}, scoped by organization here rather than by business unit, like every
 * other master in this project.
 *
 * <p>{@link #requireHolder} is what makes a document type's required party role mean something:
 * without it a booking could name a supplier and nothing would notice until a report did. It
 * runs when a document is saved, not when it is approved - a booking naming a supplier is wrong
 * the moment it is keyed, and finding out at approval costs a revision.
 */
@Service
@Transactional(readOnly = true)
public class PartyService {

    private static final int MAX_PAGE_SIZE = 100;

    /** A party as a picker draws it: code first, trading name underneath. */
    public record DirectoryRow(Long id, String code, String name, boolean active) { }

    public record DirectoryPage(java.util.List<DirectoryRow> rows, int page, int size,
                                long totalElements, int totalPages) { }

    private final PartyRepository parties;
    private final OrgContext context;

    public PartyService(PartyRepository parties, OrgContext context) {
        this.parties = parties;
        this.context = context;
    }

    /** @throws IllegalArgumentException when no such party exists in the caller's organization */
    public Party require(Long partyId) {
        return parties.findScoped(partyId, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("No party with id " + partyId));
    }

    /**
     * The party, checked against the role the document demands.
     *
     * @param role null when the document type names no particular role
     * @throws PartyRoleNotHeldException when it exists but does not hold {@code role}
     */
    public Party requireHolder(Long partyId, PartyRoleType role) {
        Party party = require(partyId);
        if (role != null) {
            party.requireRole(role);
        }
        return party;
    }

    /**
     * One page of parties holding {@code role}, matched on code or name anywhere, sorted by code -
     * the code is what the user is typing, and a list ordered by anything else appears to jump
     * around as the term narrows.
     */
    public DirectoryPage directory(PartyRoleType role, String search, boolean activeOnly, int page, int size) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.ASC, "code"));
        Page<Party> found = parties.directory(context.requireOrganizationId(), role,
            "%" + term + "%", !activeOnly, pageable);
        return new DirectoryPage(
            found.getContent().stream()
                .map(p -> new DirectoryRow(p.getId(), p.getCode(), p.getName(), p.getActive()))
                .toList(),
            found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }
}
