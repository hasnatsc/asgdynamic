package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One clause of a document's terms & conditions - asgdynamic's {@code dtlTcSet}.
 *
 * <p>The text is copied from the global {@link com.asg.fabricerp.global.terms.TermsCondition} rather than referenced, so
 * rewording a standard clause later never changes what an already-issued booking said.
 */
@Entity
@Table(
    name = "gbl_business_document_terms",
    indexes = {
        @Index(name = "ix_gbdt_document", columnList = "document_id"),
        @Index(name = "ix_gbdt_org",      columnList = "organization_id")
    })
public class BusinessDocumentTerm extends BaseOrgLineEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gbdt_document"))
    private BusinessDocument document;

    @Column(name = "serial_no", nullable = false)
    private Integer serialNo = 0;

    @NotBlank
    @Size(max = 2000)
    @Column(name = "body_text", nullable = false, length = 2000)
    private String bodyText;

    public BusinessDocumentTerm() { }

    public BusinessDocumentTerm(Integer serialNo, String bodyText) {
        this.serialNo = serialNo;
        this.bodyText = bodyText;
    }

    public BusinessDocument getDocument()       { return document; }
    public void setDocument(BusinessDocument v) { this.document = v; }
    public Integer getSerialNo()                { return serialNo; }
    public void setSerialNo(Integer v)          { this.serialNo = v; }
    public String getBodyText()                 { return bodyText; }
    public void setBodyText(String v)           { this.bodyText = v == null ? null : v.trim(); }
}
