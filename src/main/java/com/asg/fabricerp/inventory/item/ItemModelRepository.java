package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemModelRepository extends JpaRepository<ItemModel, Long> {

    @Query("""
           select e from ItemModel e left join fetch e.brand
           where e.id = :id and e.organizationId = :orgId and e.deleted = false
           """)
    Optional<ItemModel> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query(value = """
           select e from ItemModel e left join fetch e.brand b
           where e.organizationId = :orgId
             and e.deleted = false
             and (:brandId is null or b.id = :brandId)
             and (:q is null
                  or lower(e.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.name) like lower(concat('%', cast(:q as string), '%'))
                  or lower(b.name) like lower(concat('%', cast(:q as string), '%')))
           """,
           countQuery = """
           select count(e) from ItemModel e left join e.brand b
           where e.organizationId = :orgId
             and e.deleted = false
             and (:brandId is null or b.id = :brandId)
             and (:q is null
                  or lower(e.code) like lower(concat('%', cast(:q as string), '%'))
                  or lower(e.name) like lower(concat('%', cast(:q as string), '%'))
                  or lower(b.name) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<ItemModel> search(@Param("orgId") Long orgId, @Param("brandId") Long brandId,
                           @Param("q") String q, Pageable pageable);

    /** Dropdown feed; {@code brandId} null lists every brand's models. */
    @Query("""
           select e from ItemModel e
           where e.organizationId = :orgId and e.active = true and e.deleted = false
             and (:brandId is null or e.brand.id = :brandId)
           order by e.name asc
           """)
    List<ItemModel> lookup(@Param("orgId") Long orgId, @Param("brandId") Long brandId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long organizationId, String code);

    boolean existsByBrand_IdAndDeletedFalse(Long brandId);
}
