package com.asg.fabricerp.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryTypeRepository extends JpaRepository<DeliveryType, Long> {

    @Query("select t from DeliveryType t where t.organizationId = :org and t.deleted = false order by t.sortOrder, t.name")
    List<DeliveryType> list(@Param("org") Long org);

    @Query("select t from DeliveryType t where t.id = :id and t.organizationId = :org and t.deleted = false")
    Optional<DeliveryType> findScoped(@Param("id") Long id, @Param("org") Long org);

    @Query("""
        select count(t) > 0 from DeliveryType t where t.organizationId = :org and t.deleted = false
        and upper(t.code) = upper(:code) and (:id is null or t.id <> :id)
        """)
    boolean codeTaken(@Param("org") Long org, @Param("code") String code, @Param("id") Long id);
}
