package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Query("""
           select i from InventoryItem i
             join fetch i.category join fetch i.baseUnit
             left join fetch i.hsCode left join fetch i.brand left join fetch i.model
             left join fetch i.yarnType left join fetch i.yarnCount
             left join fetch i.yarnPly left join fetch i.yarnBlend
           where i.id = :id and i.organizationId = :orgId and i.deleted = false
           """)
    Optional<InventoryItem> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query(value = """
           select i from InventoryItem i join fetch i.category c join fetch i.baseUnit u
           where i.organizationId = :orgId
             and i.deleted = false
             and (:itemType is null or i.itemType = :itemType)
             and (:categoryId is null or c.id = :categoryId)
             and (:active is null or i.active = :active)
             and (:q is null
                  or lower(i.itemCode) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.name) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.sku) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.barcode) like lower(concat('%', cast(:q as string), '%')))
           """,
           countQuery = """
           select count(i) from InventoryItem i join i.category c
           where i.organizationId = :orgId
             and i.deleted = false
             and (:itemType is null or i.itemType = :itemType)
             and (:categoryId is null or c.id = :categoryId)
             and (:active is null or i.active = :active)
             and (:q is null
                  or lower(i.itemCode) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.name) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.sku) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.barcode) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<InventoryItem> search(@Param("orgId") Long orgId,
                               @Param("itemType") ItemType itemType,
                               @Param("categoryId") Long categoryId,
                               @Param("active") Boolean active,
                               @Param("q") String q,
                               Pageable pageable);

    /** Picker feed for other screens: active items, optionally of one type. */
    @Query("""
           select i from InventoryItem i join fetch i.baseUnit
           where i.organizationId = :orgId and i.active = true and i.deleted = false
             and (:itemType is null or i.itemType = :itemType)
             and (:q is null
                  or lower(i.itemCode) like lower(concat('%', cast(:q as string), '%'))
                  or lower(i.name) like lower(concat('%', cast(:q as string), '%')))
           order by i.name asc
           """)
    List<InventoryItem> lookup(@Param("orgId") Long orgId, @Param("itemType") ItemType itemType,
                               @Param("q") String q, Pageable limit);

    /** Another live item with this name, other than {@code excludeId}. */
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query("""
           select count(i) > 0 from InventoryItem i
           where i.organizationId = :orgId and lower(i.name) = lower(:name)
             and i.deleted = false and (:excludeId is null or i.id <> :excludeId)
           """)
    boolean nameTaken(@Param("orgId") Long orgId, @Param("name") String name, @Param("excludeId") Long excludeId);

    /** Active yarn items with the same type, count, ply and blend - the combination is the identity. */
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query("""
           select i from InventoryItem i
           where i.organizationId = :orgId
             and i.itemType = com.asg.fabricerp.inventory.item.ItemType.YARN
             and i.active = true and i.deleted = false
             and i.yarnType.id = :typeId and i.yarnCount.id = :countId
             and i.yarnPly.id = :plyId and i.yarnBlend.id = :blendId
           """)
    List<InventoryItem> findYarnDuplicates(@Param("orgId") Long orgId,
                                           @Param("typeId") Long typeId,
                                           @Param("countId") Long countId,
                                           @Param("plyId") Long plyId,
                                           @Param("blendId") Long blendId);

    // ---- "Is this master still in use?" - consulted before a master is deleted ------------------

    boolean existsByCategory_IdAndDeletedFalse(Long categoryId);
    boolean existsByBaseUnit_IdAndDeletedFalse(Long uomId);
    boolean existsByHsCode_IdAndDeletedFalse(Long hsCodeId);
    boolean existsByBrand_IdAndDeletedFalse(Long brandId);
    boolean existsByModel_IdAndDeletedFalse(Long modelId);
    boolean existsByYarnType_IdAndDeletedFalse(Long yarnTypeId);
    boolean existsByYarnCount_IdAndDeletedFalse(Long yarnCountId);
    boolean existsByYarnPly_IdAndDeletedFalse(Long yarnPlyId);
    boolean existsByYarnBlend_IdAndDeletedFalse(Long yarnBlendId);
}
