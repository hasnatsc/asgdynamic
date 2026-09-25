package com.asg.fabricerp.party;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    // ---- Maintenance screen ----------------------------------------------------------------------

    /**
     * The directory grid. {@code pattern} is a lower-cased {@code %term%} or null; it matches the
     * code, trading and legal names, TIN, BIN, and the identifiers a party trades under in its roles
     * (the legacy customer and supplier codes). V16 backs the name and code matches with trigram
     * indexes, so a contains-search stays an index scan as the directory grows.
     */
    @Query(value = """
           select p from Party p
           where p.organizationId = :orgId and p.deleted = false
             and (:active is null or p.active = :active)
             and (:partyType is null or p.partyType = :partyType)
             and (:role is null or exists (select 1 from PartyRole r
                                           where r.party = p and r.roleType = :role and r.current = true))
             and (:pattern is null
                  or lower(p.code) like :pattern or lower(p.name) like :pattern
                  or lower(p.legalName) like :pattern
                  or lower(p.tin) like :pattern or lower(p.bin) like :pattern
                  or exists (select 1 from PartyRole r2 where r2.party = p and lower(r2.roleCode) like :pattern))
           """,
           countQuery = """
           select count(p) from Party p
           where p.organizationId = :orgId and p.deleted = false
             and (:active is null or p.active = :active)
             and (:partyType is null or p.partyType = :partyType)
             and (:role is null or exists (select 1 from PartyRole r
                                           where r.party = p and r.roleType = :role and r.current = true))
             and (:pattern is null
                  or lower(p.code) like :pattern or lower(p.name) like :pattern
                  or lower(p.legalName) like :pattern
                  or lower(p.tin) like :pattern or lower(p.bin) like :pattern
                  or exists (select 1 from PartyRole r2 where r2.party = p and lower(r2.roleCode) like :pattern))
           """)
    Page<Party> adminSearch(@Param("orgId") Long orgId,
                            @Param("role") PartyRoleType role,
                            @Param("active") Boolean active,
                            @Param("partyType") Party.PartyType partyType,
                            @Param("pattern") String pattern,
                            Pageable pageable);

    /** Live parties per current role - the filter chips' counts, in one grouped query. */
    @Query("""
           select r.roleType, count(distinct p.id) from Party p join p.roles r
           where p.organizationId = :orgId and p.deleted = false and r.current = true
           group by r.roleType
           """)
    List<Object[]> countByRole(@Param("orgId") Long orgId);

    @Query("""
           select count(p), coalesce(sum(case when p.active = false then 1 else 0 end), 0) from Party p
           where p.organizationId = :orgId and p.deleted = false
           """)
    List<Object[]> countTotals(@Param("orgId") Long orgId);

    // Page enrichment: one query each for the rows on screen, never one per row.

    @Query("select r from PartyRole r where r.party.id in :ids and r.current = true order by r.roleType")
    List<PartyRole> currentRolesOf(@Param("ids") Collection<Long> ids);

    @Query("select c from PartyContact c where c.party.id in :ids and c.primary = true")
    List<PartyContact> primaryContactsOf(@Param("ids") Collection<Long> ids);

    @Query("select a from PartyAddress a where a.party.id in :ids and a.primary = true")
    List<PartyAddress> primaryAddressesOf(@Param("ids") Collection<Long> ids);

    // Usage - consulted before a party is deleted or loses a role others depend on.

    /** Documents naming this party. Native: the document model lives in a package that depends on this one. */
    @Query(value = "select count(*) from gbl_business_documents where party_id = :id and not deleted",
           nativeQuery = true)
    long documentsNaming(@Param("id") Long id);

    /** Other parties' accounts held at this party as their bank. */
    @Query("select count(a) from PartyBankAccount a where a.bank.id = :id and a.party.id <> :id")
    long accountsHeldAt(@Param("id") Long id);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);
}
