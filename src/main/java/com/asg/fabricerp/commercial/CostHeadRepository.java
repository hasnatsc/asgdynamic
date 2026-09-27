package com.asg.fabricerp.commercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CostHeadRepository extends JpaRepository<CostHead, Long> {

    @Query("select c from CostHead c where c.organizationId = :org and c.deleted = false order by c.docKind, c.name")
    List<CostHead> list(@Param("org") Long org);

    @Query("select c from CostHead c where c.id = :id and c.organizationId = :org and c.deleted = false")
    Optional<CostHead> findScoped(@Param("id") Long id, @Param("org") Long org);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long org, String code);
}
