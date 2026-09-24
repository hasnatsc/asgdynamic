package com.asg.fabricerp.inventory.item;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

    @Query("""
           select c from ItemCategory c left join fetch c.parent
           where c.id = :id and c.organizationId = :orgId and c.deleted = false
           """)
    Optional<ItemCategory> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /** The whole tree for one tenant, small enough to build in memory. */
    @Query("""
           select c from ItemCategory c left join fetch c.parent
           where c.organizationId = :orgId and c.deleted = false
           order by c.code asc
           """)
    List<ItemCategory> findTree(@Param("orgId") Long orgId);

    /** Codes of every root, for numbering the next one. Deleted rows count: codes are never reissued. */
    @Query("""
           select c.code from ItemCategory c
           where c.organizationId = :orgId and c.parent is null
           """)
    List<String> rootCodes(@Param("orgId") Long orgId);

    /** Codes of {@code parentId}'s children, deleted ones included for the same reason. */
    @Query("""
           select c.code from ItemCategory c
           where c.organizationId = :orgId and c.parent.id = :parentId
           """)
    List<String> childCodes(@Param("orgId") Long orgId, @Param("parentId") Long parentId);

    boolean existsByParent_IdAndDeletedFalse(Long parentId);

    /** Item-layer categories a picker may offer, optionally narrowed to one item type. */
    @Query("""
           select c from ItemCategory c left join fetch c.parent
           where c.organizationId = :orgId and c.active = true and c.deleted = false
             and c.layer = com.asg.fabricerp.inventory.item.ItemCategory.Layer.ITEM
             and (:itemType is null or c.itemType = :itemType)
           order by c.name asc
           """)
    List<ItemCategory> itemLayerLookup(@Param("orgId") Long orgId, @Param("itemType") ItemType itemType);
}
