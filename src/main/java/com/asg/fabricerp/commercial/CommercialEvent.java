package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.EventKind;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One dated record against a commercial document after it is raised: a UD or UP, a back-to-back
 * raw material LC, a sales contract, a required document, a cost, a CI's realization step, an import
 * PI's milestone. Written by {@link CommercialRecordsService}; never edited, only removed.
 */
@Entity
@Table(name = "com_document_events")
public class CommercialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventKind kind;

    /** Realization step, milestone, or a BTB LC's material. */
    @Column(length = 30)            private String code;
    @Column(name = "ref_no", length = 100) private String refNo;
    @Column(name = "event_date")    private LocalDate eventDate;
    @Column(precision = 20, scale = 4) private BigDecimal amount;
    @Column(name = "party_id")      private Long partyId;
    @Column(name = "cost_head_id")  private Long costHeadId;
    @Column(name = "document_name_id") private Long documentNameId;
    @Column(length = 500)           private String remarks;
    @Column(name = "recorded_by", length = 100) private String recordedBy;
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt = LocalDateTime.now();

    protected CommercialEvent() { }

    public CommercialEvent(Long organizationId, Long documentId, EventKind kind) {
        this.organizationId = organizationId;
        this.documentId = documentId;
        this.kind = kind;
    }

    /** A copy for a revision - the amended LC keeps its UDs, contracts and costs. */
    public CommercialEvent copyFor(Long revisionId) {
        CommercialEvent e = new CommercialEvent(organizationId, revisionId, kind);
        e.code = code; e.refNo = refNo; e.eventDate = eventDate; e.amount = amount; e.partyId = partyId;
        e.costHeadId = costHeadId; e.documentNameId = documentNameId; e.remarks = remarks;
        e.recordedBy = recordedBy; e.recordedAt = recordedAt;
        return e;
    }

    public Long getId()                    { return id; }
    public Long getOrganizationId()        { return organizationId; }
    public Long getDocumentId()            { return documentId; }
    public EventKind getKind()             { return kind; }
    public String getCode()                { return code; }
    public void setCode(String v)          { this.code = v; }
    public String getRefNo()               { return refNo; }
    public void setRefNo(String v)         { this.refNo = v; }
    public LocalDate getEventDate()        { return eventDate; }
    public void setEventDate(LocalDate v)  { this.eventDate = v; }
    public BigDecimal getAmount()          { return amount; }
    public void setAmount(BigDecimal v)    { this.amount = v; }
    public Long getPartyId()               { return partyId; }
    public void setPartyId(Long v)         { this.partyId = v; }
    public Long getCostHeadId()            { return costHeadId; }
    public void setCostHeadId(Long v)      { this.costHeadId = v; }
    public Long getDocumentNameId()        { return documentNameId; }
    public void setDocumentNameId(Long v)  { this.documentNameId = v; }
    public String getRemarks()             { return remarks; }
    public void setRemarks(String v)       { this.remarks = v; }
    public String getRecordedBy()          { return recordedBy; }
    public void setRecordedBy(String v)    { this.recordedBy = v; }
    public LocalDateTime getRecordedAt()   { return recordedAt; }
}
