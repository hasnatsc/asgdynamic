package com.asg.fabricerp.commercial;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;

/** A document an LC requires or a CI is presented with - the legacy "Document Names". */
@Entity
@Table(name = "com_document_names")
public class DocumentName extends BaseOrgEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** PI, LC or CI: where it is picked. */
    @Column(name = "doc_kind", nullable = false, length = 4)
    private String docKind;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public String getCode()             { return code; }
    public void setCode(String v)       { this.code = v; }
    public String getName()             { return name; }
    public void setName(String v)       { this.name = v; }
    public String getDocKind()          { return docKind; }
    public void setDocKind(String v)    { this.docKind = v; }
    public Integer getSortOrder()       { return sortOrder; }
    public void setSortOrder(Integer v) { this.sortOrder = v == null ? 0 : v; }
}
