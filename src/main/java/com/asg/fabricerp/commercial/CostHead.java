package com.asg.fabricerp.commercial;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;

/** A head commercial costs are recorded under - the legacy "LC Cost Head". */
@Entity
@Table(name = "com_cost_heads")
public class CostHead extends BaseOrgEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** PI, LC or CI: where it is recorded. */
    @Column(name = "doc_kind", nullable = false, length = 4)
    private String docKind;

    /** The ledger account the cost is charged to, by code - as posting rules name accounts. */
    @Column(name = "account_code", length = 40)
    private String accountCode;

    public String getCode()              { return code; }
    public void setCode(String v)        { this.code = v; }
    public String getName()              { return name; }
    public void setName(String v)        { this.name = v; }
    public String getDocKind()           { return docKind; }
    public void setDocKind(String v)     { this.docKind = v; }
    public String getAccountCode()       { return accountCode; }
    public void setAccountCode(String v) { this.accountCode = v; }
}
