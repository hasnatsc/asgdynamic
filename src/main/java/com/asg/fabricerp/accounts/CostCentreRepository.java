package com.asg.fabricerp.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CostCentreRepository extends JpaRepository<CostCentre, Long> {

    @Query("select c from CostCentre c left join fetch c.parent left join fetch c.absorbsInto where c.organizationId = :orgId and c.deleted = false order by c.code")
    List<CostCentre> all(@Param("orgId") Long orgId);

    @Query("select c from CostCentre c where c.organizationId = :orgId and c.code = :code and c.deleted = false")
    Optional<CostCentre> findByCode(@Param("orgId") Long orgId, @Param("code") String code);

    @Query("select c from CostCentre c where c.id = :id and c.organizationId = :orgId and c.deleted = false")
    Optional<CostCentre> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}
