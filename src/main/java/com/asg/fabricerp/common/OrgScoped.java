package com.asg.fabricerp.common;

/**
 * Implemented by any entity that belongs to a tenant.
 *
 * <p>Deliberately an id, not a {@code @ManyToOne Organization}. SpindleERP maps the
 * association, which means every insert triggers a reference lookup and every query
 * carries a join it rarely needs. Scoping is a filter predicate, not a navigation.
 */
public interface OrgScoped {
    Long getOrganizationId();
    void setOrganizationId(Long organizationId);
}
