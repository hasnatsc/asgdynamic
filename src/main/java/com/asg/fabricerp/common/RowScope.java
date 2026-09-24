package com.asg.fabricerp.common;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which rows a user may see — asfl-erp's {@code UserScope} (ADM-3, ADM-5).
 *
 * <p>Resolved once per request onto the principal and carried, rather than re-queried per
 * grid: a grid, a picker and a detail screen must narrow identically, and asking three times
 * invites three answers.
 *
 * <h2>Unrestricted is a state, not an empty map</h2>
 * ADM-4's unrestricted users (MD, Accounts, Commercial) see everything. Representing that as
 * "no scope rows" would make it indistinguishable from a user nobody has configured yet, and
 * the safe readings of those two cases are opposite. So {@link #unrestricted} is explicit and
 * {@link #isConfigured()} catches the other case.
 *
 * @param values one entry per dimension the user is restricted along; a dimension absent from
 *               the map is unrestricted for that dimension alone
 */
public record RowScope(boolean unrestricted, Map<ScopeDimension, Set<Long>> values) {

    /**
     * Stands in for an empty id list in an {@code IN (...)} clause when the dimension is not
     * restricted and the list is ignored anyway. No row has a negative id.
     */
    private static final List<Long> NO_IDS = List.of(-1L);

    public RowScope {
        Map<ScopeDimension, Set<Long>> copy = new EnumMap<>(ScopeDimension.class);
        values.forEach((dimension, ids) -> copy.put(dimension, Set.copyOf(ids)));
        values = Map.copyOf(copy);
    }

    public static RowScope unrestrictedScope() {
        return new RowScope(true, Map.of());
    }

    /** Whether this dimension narrows anything for this user. */
    public boolean restricts(ScopeDimension dimension) {
        return !unrestricted && values.containsKey(dimension);
    }

    /**
     * Whether a value on a dimension is visible. An unlisted dimension permits everything on
     * it; a restricted one permits only its listed ids, and never a null.
     */
    public boolean permits(ScopeDimension dimension, Long value) {
        if (!restricts(dimension)) {
            return true;
        }
        return value != null && values.get(dimension).contains(value);
    }

    public Set<Long> allowedOn(ScopeDimension dimension) {
        return values.getOrDefault(dimension, Set.of());
    }

    /** For a JPQL {@code IN} parameter: the allowed ids, or a placeholder when not restricted. */
    public List<Long> idsForQuery(ScopeDimension dimension) {
        return restricts(dimension) ? List.copyOf(allowedOn(dimension)) : NO_IDS;
    }

    /** ADM-4: the single team a restricted user belongs to, when there is exactly one. */
    public Long soleMarketingTeam() {
        Set<Long> teams = allowedOn(ScopeDimension.MARKETING_TEAM);
        return !unrestricted && teams.size() == 1 ? teams.iterator().next() : null;
    }

    /**
     * Whether anybody has decided what this user may see. A restricted user with no scope rows
     * is unconfigured — not omniscient and not blind — and is refused at login.
     */
    public boolean isConfigured() {
        return unrestricted || !values.isEmpty();
    }
}
