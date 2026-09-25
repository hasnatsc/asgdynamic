package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every finder is org-scoped and soft-delete aware by construction.
 *
 * <p>In SpindleERP that is a convention each query has to remember, which is exactly the
 * kind of rule that holds for two years and then does not. Here there is no unscoped
 * finder to call by accident: {@code findById} is deliberately not used by services —
 * {@link #findScoped} is.
 */
public interface BusinessDocumentRepository extends JpaRepository<BusinessDocument, Long> {

    @Query("""
           select d from BusinessDocument d
           where d.id = :id
             and d.organizationId = :orgId
             and d.deleted = false
           """)
    Optional<BusinessDocument> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    /**
     * The document with both levels of lines loaded - almost every caller needs them, and
     * controllers read them after the transaction has closed ({@code open-in-view} is off).
     *
     * <p>Two queries, not one entity graph. The graph this replaced asked for
     * {@code lineGroups} and {@code lineGroups.colorLines} together; both are {@code List}s
     * ("bags"), and Hibernate refuses to join-fetch two bags at once
     * ({@code MultipleBagFetchException}) because the cartesian product would duplicate rows.
     * Every detail, edit, revise and draw failed that way against a real database; the unit
     * tests mock this method and never ran the query. Loading the colour lines second, in the
     * same persistence context, initialises every group's collection and keeps the lists -
     * whose order the line numbering depends on - as lists.
     *
     * <p>Must run inside a transaction so both queries share one persistence context; every
     * caller is a {@code @Transactional} service method, and this one joins it.
     */
    @Transactional(readOnly = true)
    default Optional<BusinessDocument> findScopedWithLines(Long id, Long orgId) {
        Optional<BusinessDocument> document = findScopedWithGroups(id, orgId);
        document.ifPresent(this::fetchColorLines);
        return document;
    }

    /** Use {@link #findScopedWithLines}: this loads only the first level. */
    @EntityGraph(attributePaths = "lineGroups")
    @Query("""
           select d from BusinessDocument d
           where d.id = :id
             and d.organizationId = :orgId
             and d.deleted = false
           """)
    Optional<BusinessDocument> findScopedWithGroups(@Param("id") Long id, @Param("orgId") Long orgId);

    /** Use {@link #findScopedWithLines}: initialises the colour lines of a loaded document's groups. */
    @Query("""
           select distinct g from BusinessDocumentLineGroup g
             left join fetch g.colorLines
           where g.document = :document
           """)
    List<BusinessDocumentLineGroup> fetchColorLines(@Param("document") BusinessDocument document);

    /**
     * A list screen's query, narrowed by the caller's row scope (ADM-3, ADM-5).
     *
     * <p>The scope is a required argument rather than something a caller may add, for the same
     * reason {@link #findScoped} exists: an unscoped variant would be the one somebody calls.
     * The predicate must agree with {@link BusinessDocument#isVisibleTo}, which applies the
     * same rule to a single document.
     */
    default Page<BusinessDocument> search(Long orgId, Long unitId, DocumentType type,
                                          BusinessDocumentStatus status,
                                          LocalDate from, LocalDate to, String q,
                                          RowScope scope, Pageable pageable) {
        return searchWithin(orgId, unitId, type, status, from, to, q,
            !scope.restricts(ScopeDimension.BUSINESS_UNIT),
            scope.idsForQuery(ScopeDimension.BUSINESS_UNIT),
            !scope.restricts(ScopeDimension.WAREHOUSE),
            scope.idsForQuery(ScopeDimension.WAREHOUSE),
            !scope.restricts(ScopeDimension.MARKETING_TEAM),
            scope.idsForQuery(ScopeDimension.MARKETING_TEAM),
            pageable);
    }

    /** Use {@link #search} — this is its query, with the scope already unpacked. */
    @Query("""
           select d from BusinessDocument d
           where d.organizationId = :orgId
             and d.businessUnit.id = :unitId
             and d.documentType = :type
             and d.deleted = false
             and (:status is null or d.status = :status)
             and (:from is null or d.documentDate >= :from)
             and (:to is null or d.documentDate <= :to)
             and (:q is null
                  or lower(d.documentNo) like lower(concat('%', cast(:q as string), '%'))
                  or lower(d.referenceNo) like lower(concat('%', cast(:q as string), '%')))
             and (:allUnits = true or d.businessUnit.id in :unitIds)
             and (:allWarehouses = true or d.warehouse is null or d.warehouse.id in :warehouseIds)
             and (:allTeams = true or d.marketingTeam.id in :teamIds)
           """)
    Page<BusinessDocument> searchWithin(@Param("orgId") Long orgId,
                                        @Param("unitId") Long unitId,
                                        @Param("type") DocumentType type,
                                        @Param("status") BusinessDocumentStatus status,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("q") String q,
                                        @Param("allUnits") boolean allUnits,
                                        @Param("unitIds") List<Long> unitIds,
                                        @Param("allWarehouses") boolean allWarehouses,
                                        @Param("warehouseIds") List<Long> warehouseIds,
                                        @Param("allTeams") boolean allTeams,
                                        @Param("teamIds") List<Long> teamIds,
                                        Pageable pageable);

    /** Revision chain for a document, newest first. */
    @Query("""
           select d from BusinessDocument d
           where d.organizationId = :orgId
             and (d.id = :rootId or d.revisionOf.id = :rootId)
             and d.deleted = false
           order by d.revisionNo desc
           """)
    List<BusinessDocument> revisionsOf(@Param("rootId") Long rootId, @Param("orgId") Long orgId);

    /** Children of a document, e.g. the BPOs raised against a Booking. */
    @Query("""
           select d from BusinessDocument d
           where d.organizationId = :orgId
             and d.parentDocument.id = :parentId
             and d.deleted = false
           """)
    List<BusinessDocument> childrenOf(@Param("parentId") Long parentId, @Param("orgId") Long orgId);

    boolean existsByOrganizationIdAndDocumentNo(Long organizationId, String documentNo);
}
