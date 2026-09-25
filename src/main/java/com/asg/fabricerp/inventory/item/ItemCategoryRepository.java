package com.asg.fabricerp.inventory.item;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** One page of active categories on {@code layers}, searched by name or code, in code order. */
    @Query("""
           select c from ItemCategory c left join fetch c.parent
           where c.organizationId = :orgId and c.active = true and c.deleted = false
             and c.layer in :layers
             and (:itemType is null or c.itemType = :itemType)
             and (:excludeId is null or c.id <> :excludeId)
             and (lower(c.name) like :q escape '\\' or lower(c.code) like :q escape '\\')
           order by c.code asc
           """)
    Slice<ItemCategory> search(@Param("orgId") Long orgId, @Param("layers") Collection<ItemCategory.Layer> layers,
                               @Param("itemType") ItemType itemType, @Param("excludeId") Long excludeId,
                               @Param("q") String q, Pageable pageable);
}
