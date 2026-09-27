package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.EventKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommercialEventRepository extends JpaRepository<CommercialEvent, Long> {

    List<CommercialEvent> findByDocumentIdOrderByEventDateAscIdAsc(Long documentId);

    List<CommercialEvent> findByDocumentIdAndKindOrderByEventDateAscIdAsc(Long documentId, EventKind kind);
}
