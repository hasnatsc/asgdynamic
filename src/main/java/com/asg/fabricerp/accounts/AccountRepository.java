package com.asg.fabricerp.accounts;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("select a from Account a left join fetch a.parent where a.organizationId = :orgId and a.deleted = false order by a.code")
    List<Account> chart(@Param("orgId") Long orgId);

    @Query("select a from Account a where a.organizationId = :orgId and a.code = :code and a.deleted = false")
    Optional<Account> findByCode(@Param("orgId") Long orgId, @Param("code") String code);

    @Query("select a from Account a where a.id = :id and a.organizationId = :orgId and a.deleted = false")
    Optional<Account> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query("select count(a) from Account a where a.parent.id = :parentId and a.deleted = false")
    long countChildren(@Param("parentId") Long parentId);

    @Query("select count(a) from Account a where a.organizationId = :orgId and a.deleted = false")
    long countInChart(@Param("orgId") Long orgId);

    /**
     * One page of accounts a new {@code type} account may sit under: same type, active, not a
     * control account, and without postings (a parent becomes a summary and takes no entries).
     */
    @Query("""
           select a from Account a left join fetch a.parent
           where a.organizationId = :orgId and a.deleted = false and a.active = true
             and a.accountType = :type
             and a.usage <> com.asg.fabricerp.accounts.AccountFlags.AccountUsage.CONTROL
             and not exists (select 1 from GlEntryLine l join l.entry e
                             where e.organizationId = :orgId and l.accountCode = a.code)
             and (lower(a.code) like :q escape '\\' or lower(a.name) like :q escape '\\')
           order by a.code asc
           """)
    Slice<Account> parentCandidates(@Param("orgId") Long orgId, @Param("type") AccountFlags.AccountType type,
                                    @Param("q") String q, Pageable pageable);
}
