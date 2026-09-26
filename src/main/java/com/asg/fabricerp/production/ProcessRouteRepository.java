package com.asg.fabricerp.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcessRouteRepository extends JpaRepository<ProcessRoute, Long> {

    @Query("""
           select r from ProcessRoute r
           where r.organizationId = :orgId and r.deleted = false
           order by r.routeCode, r.fabricType
           """)
    List<ProcessRoute> findLive(@Param("orgId") Long orgId);

    @Query("""
           select r from ProcessRoute r
           where r.organizationId = :orgId and r.deleted = false
             and lower(trim(r.fabricType)) = lower(trim(:fabricType))
           """)
    Optional<ProcessRoute> findForFabricType(@Param("orgId") Long orgId, @Param("fabricType") String fabricType);

    @Query("""
           select r from ProcessRoute r
           where r.id = :id and r.organizationId = :orgId and r.deleted = false
           """)
    Optional<ProcessRoute> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);
}
