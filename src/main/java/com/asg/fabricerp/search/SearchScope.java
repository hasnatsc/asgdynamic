package com.asg.fabricerp.search;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.global.documents.DocumentType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What one user's search may return - the same narrowing as each screen's own list, so search
 * never shows a row the list would not:
 * <ul>
 *   <li>the organization they work in, and for documents the business unit too (every document
 *       list is per unit);</li>
 *   <li>only kinds and document types they hold VIEW on;</li>
 *   <li>their marketing-team and store scope, as {@code BusinessDocument.isVisibleTo};</li>
 *   <li>bookings they created themselves - the booking list's own rule.</li>
 * </ul>
 *
 * @param documentTypes  document types (enum names) the user may see
 * @param kinds          kinds the user may see at all; DOCUMENT only when {@code documentTypes} is not empty
 * @param teamIds        null when not restricted by marketing team
 * @param warehouseIds   null when not restricted by store
 */
public record SearchScope(Long organizationId, Long businessUnitId, String username,
                          Set<SearchKind> kinds, List<String> documentTypes,
                          List<Long> teamIds, List<Long> warehouseIds) {

    /** The booking list shows its creator's bookings only; search does the same. */
    public static final String OWNER_ONLY_TYPE = DocumentType.BOOKING.name();

    public static SearchScope of(OrgContext context, Set<String> authorities) {
        List<String> types = Arrays.stream(DocumentType.values())
            .map(Enum::name)
            .filter(t -> {
                String needed = SearchKind.viewAuthority(SearchKind.DOCUMENT, t);
                return needed != null && authorities.contains(needed);
            })
            .toList();
        Set<SearchKind> kinds = EnumSet.noneOf(SearchKind.class);
        if (!types.isEmpty() && context.businessUnitId() != null) kinds.add(SearchKind.DOCUMENT);
        for (SearchKind k : List.of(SearchKind.VOUCHER, SearchKind.PARTY, SearchKind.ITEM)) {
            if (authorities.contains(SearchKind.viewAuthority(k, null))) kinds.add(k);
        }
        RowScope rows = context.requireRowScope();
        return new SearchScope(context.requireOrganizationId(), context.businessUnitId(), context.username(),
            Set.copyOf(kinds), types,
            rows.restricts(ScopeDimension.MARKETING_TEAM) ? List.copyOf(rows.allowedOn(ScopeDimension.MARKETING_TEAM)) : null,
            rows.restricts(ScopeDimension.WAREHOUSE) ? List.copyOf(rows.allowedOn(ScopeDimension.WAREHOUSE)) : null);
    }

    public boolean sees(SearchKind kind) {
        return kinds.contains(kind);
    }

    public boolean seesNothing() {
        return kinds.isEmpty();
    }
}
