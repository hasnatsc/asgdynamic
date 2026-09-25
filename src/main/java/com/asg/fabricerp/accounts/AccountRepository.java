package com.asg.fabricerp.accounts;

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
}
