package com.asg.fabricerp.party;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    /** Roles fetched with the party: every caller that loads one is about to check a role. */
    @EntityGraph(attributePaths = "roles")
    @Query("""
           select p from Party p
           where p.id = :id and p.organizationId = :orgId and p.deleted = false
           """)
    Optional<Party> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query("""
           select p from Party p
           where p.organizationId = :orgId and lower(p.code) = lower(:code) and p.deleted = false
           """)
    Optional<Party> findByCode(@Param("orgId") Long orgId, @Param("code") String code);

    /**
     * A page of parties holding a current role, matched on a typed term - the picker's query.
     * Matching and paging happen in PostgreSQL, not in a stream over the whole directory.
     *
     * <p>{@code includeInactive} relaxes the <em>party's</em> status, never the <em>role's</em>:
     * a retired customer stays findable so history renders, but a party whose customer role was
     * withdrawn is not a customer and must not appear in a customer picker at any setting.
     */
    @Query(value = """
           select p from Party p
           where p.organizationId = :orgId and p.deleted = false
             and (:includeInactive = true or p.active = true)
             and (lower(p.code) like :pattern or lower(p.name) like :pattern)
             and exists (select 1 from PartyRole r
                         where r.party = p and r.roleType = :role and r.current = true)
           """,
           countQuery = """
           select count(p) from Party p
           where p.organizationId = :orgId and p.deleted = false
             and (:includeInactive = true or p.active = true)
             and (lower(p.code) like :pattern or lower(p.name) like :pattern)
             and exists (select 1 from PartyRole r
                         where r.party = p and r.roleType = :role and r.current = true)
           """)
    Page<Party> directory(@Param("orgId") Long orgId,
                          @Param("role") PartyRoleType role,
                          @Param("pattern") String pattern,
                          @Param("includeInactive") boolean includeInactive,
                          Pageable pageable);
}
