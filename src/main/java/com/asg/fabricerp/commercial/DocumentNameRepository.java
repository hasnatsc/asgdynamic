package com.asg.fabricerp.commercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentNameRepository extends JpaRepository<DocumentName, Long> {

    @Query("select d from DocumentName d where d.organizationId = :org and d.deleted = false order by d.docKind, d.sortOrder, d.name")
    List<DocumentName> list(@Param("org") Long org);

    @Query("select d from DocumentName d where d.id = :id and d.organizationId = :org and d.deleted = false")
    Optional<DocumentName> findScoped(@Param("id") Long id, @Param("org") Long org);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(Long org, String code);
}
