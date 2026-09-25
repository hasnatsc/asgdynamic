package com.asg.fabricerp.approval;

import java.util.Set;

/**
 * Who may decide one level - asfl-erp's {@code Approver}, plus the rule this system had before
 * matrices existed.
 *
 * <ul>
 *   <li>{@link Kind#ROLE} - anyone holding the role;</li>
 *   <li>{@link Kind#USER} - that one person;</li>
 *   <li>{@link Kind#AUTHORITY} - anyone holding the screen's {@code APPROVE} verb. Used only when a
 *       document type has no matrix at all, so an unconfigured type keeps working exactly as it
 *       did rather than being blocked, and is never auto-approved either.</li>
 * </ul>
 */
public record Approver(Kind kind, Long roleId, Long userId, String authority) {

    public enum Kind { ROLE, USER, AUTHORITY }

    /** The person acting: who they are, which roles they hold, which authorities those grant. */
    public record Actor(Long userId, String username, Set<Long> roleIds, Set<String> authorities) { }

    public static Approver role(Long roleId)          { return new Approver(Kind.ROLE, roleId, null, null); }
    public static Approver user(Long userId)          { return new Approver(Kind.USER, null, userId, null); }
    public static Approver authority(String authority) { return new Approver(Kind.AUTHORITY, null, null, authority); }

    public boolean isSatisfiedBy(Actor actor) {
        return switch (kind) {
            case ROLE      -> actor.roleIds().contains(roleId);
            case USER      -> userId.equals(actor.userId());
            case AUTHORITY -> actor.authorities().contains(authority);
        };
    }
}
