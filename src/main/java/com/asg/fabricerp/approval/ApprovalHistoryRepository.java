package com.asg.fabricerp.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
