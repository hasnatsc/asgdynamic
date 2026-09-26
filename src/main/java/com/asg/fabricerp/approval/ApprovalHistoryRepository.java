package com.asg.fabricerp.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    /** Whether this person has recorded anything on the document - an approver who signed a level may still read it. */
    boolean existsByDocumentIdAndCreatedByIgnoreCase(Long documentId, String createdBy);
}
