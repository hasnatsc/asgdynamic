package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What only commercial paper carries, one row per commercial document beside its
 * {@code gbl_business_documents} row: banks and accounts, LC numbers and dates, tenure, payment and
 * INCO terms, weights, HS code, bill of entry. Which fields a document uses depends on its
 * {@link CommercialStep}; the service decides, the screens show the ones that apply.
 *
 * <p>References are ids, checked by {@link CommercialDocumentService} when saved: each must be the
 * organization's own, and banks must hold the BANK role.
 */
@Entity
@Table(name = "com_document_details")
public class CommercialDetails {

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Version
    private Long version;

    @Column(name = "validity_date")          private LocalDate validityDate;
    @Column(name = "shipment_date")          private LocalDate shipmentDate;
    @Column(name = "issue_date")             private LocalDate issueDate;
    @Column(name = "lc_no", length = 80)     private String lcNo;
    @Column(name = "master_lc_no", length = 80) private String masterLcNo;
    @Column(name = "master_lc_date")         private LocalDate masterLcDate;

    @Enumerated(EnumType.STRING) @Column(length = 20)                  private Tenure tenure;
    @Enumerated(EnumType.STRING) @Column(name = "payment_terms", length = 30) private PaymentTerms paymentTerms;
    @Enumerated(EnumType.STRING) @Column(name = "delivery_terms", length = 10) private IncoTerms deliveryTerms;

    @Column(name = "bank_party_id")           private Long bankId;
    @Column(name = "bank_account_id")         private Long bankAccountId;
    @Column(name = "counter_bank_party_id")   private Long counterBankId;
    @Column(name = "counter_bank_account_id") private Long counterBankAccountId;
    @Column(name = "foreign_bank_name", length = 200)   private String foreignBankName;
    @Column(name = "foreign_bank_bin", length = 40)     private String foreignBankBin;
    @Column(name = "foreign_bank_swift", length = 20)   private String foreignBankSwift;
    @Column(name = "foreign_bank_routing", length = 40) private String foreignBankRouting;
    @Column(name = "beneficiary_account_no", length = 60) private String beneficiaryAccountNo;

    @Column(name = "hs_code_id")                          private Long hsCodeId;
    @Column(name = "applicant_bond_licence", length = 80) private String applicantBondLicence;
    @Column(name = "net_weight", precision = 14, scale = 3)        private BigDecimal netWeight;
    @Column(name = "gross_weight", precision = 14, scale = 3)      private BigDecimal grossWeight;
    @Column(name = "calc_net_weight", precision = 14, scale = 3)   private BigDecimal calcNetWeight;
    @Column(name = "calc_gross_weight", precision = 14, scale = 3) private BigDecimal calcGrossWeight;
    @Column(name = "amount_in_words", length = 500)                private String amountInWords;
    @Column(name = "partial_shipment", nullable = false) private Boolean partialShipment = Boolean.TRUE;
    @Column(name = "btma_certificate", nullable = false) private Boolean btmaCertificate = Boolean.FALSE;
    @Column(name = "acknowledged_on")                    private LocalDate acknowledgedOn;

    @Enumerated(EnumType.STRING) @Column(name = "ci_kind", length = 10) private CiKind ciKind;
    @Column(name = "ibc_no", length = 60)                                private String ibcNo;
    @Enumerated(EnumType.STRING) @Column(name = "realization_step", length = 30) private RealizationStep realizationStep;

    @Enumerated(EnumType.STRING) @Column(name = "import_doc_type", length = 10) private ImportDocType importDocType;
    @Enumerated(EnumType.STRING) @Column(name = "lc_type", length = 12)         private LcType lcType;
    @Column(length = 30)                          private String port;
    @Column(name = "cnf_agent", length = 150)     private String cnfAgent;
    @Column(name = "ip_no", length = 60)          private String ipNo;
    @Column(name = "sro_benefited", nullable = false) private Boolean sroBenefited = Boolean.FALSE;
    @Column(name = "btma_no", length = 60)        private String btmaNo;
    @Column(name = "btma_date")                   private LocalDate btmaDate;
    @Column(name = "bill_of_entry_no", length = 60) private String billOfEntryNo;
    @Column(name = "bill_of_entry_date")          private LocalDate billOfEntryDate;
    @Column(name = "local_agent_party_id")        private Long localAgentId;
    @Column(name = "backed_by_document_id")       private Long backedByDocumentId;

    @Column(name = "incentive_amount", precision = 20, scale = 4) private BigDecimal incentiveAmount;
    @Column(name = "incentive_applied_on")                         private LocalDate incentiveAppliedOn;
    @Column(name = "incentive_confirmed_on")                       private LocalDate incentiveConfirmedOn;

    protected CommercialDetails() { }

    public CommercialDetails(Long documentId, Long organizationId) {
        this.documentId = documentId;
        this.organizationId = organizationId;
    }

    /** A copy for a revision: every fact carries over except what records the old version's own progress. */
    public CommercialDetails copyFor(Long revisionId) {
        CommercialDetails c = new CommercialDetails(revisionId, organizationId);
        c.validityDate = validityDate; c.shipmentDate = shipmentDate; c.issueDate = issueDate; c.lcNo = lcNo;
        c.masterLcNo = masterLcNo; c.masterLcDate = masterLcDate; c.tenure = tenure; c.paymentTerms = paymentTerms;
        c.deliveryTerms = deliveryTerms; c.bankId = bankId; c.bankAccountId = bankAccountId; c.counterBankId = counterBankId;
        c.counterBankAccountId = counterBankAccountId; c.foreignBankName = foreignBankName; c.foreignBankBin = foreignBankBin;
        c.foreignBankSwift = foreignBankSwift; c.foreignBankRouting = foreignBankRouting; c.beneficiaryAccountNo = beneficiaryAccountNo;
        c.hsCodeId = hsCodeId; c.applicantBondLicence = applicantBondLicence; c.netWeight = netWeight; c.grossWeight = grossWeight;
        c.calcNetWeight = calcNetWeight; c.calcGrossWeight = calcGrossWeight; c.amountInWords = amountInWords;
        c.partialShipment = partialShipment; c.btmaCertificate = btmaCertificate; c.ciKind = ciKind;
        c.importDocType = importDocType; c.lcType = lcType; c.port = port; c.cnfAgent = cnfAgent; c.ipNo = ipNo;
        c.sroBenefited = sroBenefited; c.btmaNo = btmaNo; c.btmaDate = btmaDate; c.billOfEntryNo = billOfEntryNo;
        c.billOfEntryDate = billOfEntryDate; c.localAgentId = localAgentId; c.backedByDocumentId = backedByDocumentId;
        c.incentiveAmount = incentiveAmount; c.incentiveAppliedOn = incentiveAppliedOn; c.incentiveConfirmedOn = incentiveConfirmedOn;
        return c;
    }

    public Long getDocumentId()                 { return documentId; }
    public Long getOrganizationId()             { return organizationId; }
    public LocalDate getValidityDate()          { return validityDate; }
    public void setValidityDate(LocalDate v)    { this.validityDate = v; }
    public LocalDate getShipmentDate()          { return shipmentDate; }
    public void setShipmentDate(LocalDate v)    { this.shipmentDate = v; }
    public LocalDate getIssueDate()             { return issueDate; }
    public void setIssueDate(LocalDate v)       { this.issueDate = v; }
    public String getLcNo()                     { return lcNo; }
    public void setLcNo(String v)               { this.lcNo = v; }
    public String getMasterLcNo()               { return masterLcNo; }
    public void setMasterLcNo(String v)         { this.masterLcNo = v; }
    public LocalDate getMasterLcDate()          { return masterLcDate; }
    public void setMasterLcDate(LocalDate v)    { this.masterLcDate = v; }
    public Tenure getTenure()                   { return tenure; }
    public void setTenure(Tenure v)             { this.tenure = v; }
    public PaymentTerms getPaymentTerms()       { return paymentTerms; }
    public void setPaymentTerms(PaymentTerms v) { this.paymentTerms = v; }
    public IncoTerms getDeliveryTerms()         { return deliveryTerms; }
    public void setDeliveryTerms(IncoTerms v)   { this.deliveryTerms = v; }
    public Long getBankId()                     { return bankId; }
    public void setBankId(Long v)               { this.bankId = v; }
    public Long getBankAccountId()              { return bankAccountId; }
    public void setBankAccountId(Long v)        { this.bankAccountId = v; }
    public Long getCounterBankId()              { return counterBankId; }
    public void setCounterBankId(Long v)        { this.counterBankId = v; }
    public Long getCounterBankAccountId()       { return counterBankAccountId; }
    public void setCounterBankAccountId(Long v) { this.counterBankAccountId = v; }
    public String getForeignBankName()          { return foreignBankName; }
    public void setForeignBankName(String v)    { this.foreignBankName = v; }
    public String getForeignBankBin()           { return foreignBankBin; }
    public void setForeignBankBin(String v)     { this.foreignBankBin = v; }
    public String getForeignBankSwift()         { return foreignBankSwift; }
    public void setForeignBankSwift(String v)   { this.foreignBankSwift = v; }
    public String getForeignBankRouting()       { return foreignBankRouting; }
    public void setForeignBankRouting(String v) { this.foreignBankRouting = v; }
    public String getBeneficiaryAccountNo()     { return beneficiaryAccountNo; }
    public void setBeneficiaryAccountNo(String v) { this.beneficiaryAccountNo = v; }
    public Long getHsCodeId()                   { return hsCodeId; }
    public void setHsCodeId(Long v)             { this.hsCodeId = v; }
    public String getApplicantBondLicence()     { return applicantBondLicence; }
    public void setApplicantBondLicence(String v) { this.applicantBondLicence = v; }
    public BigDecimal getNetWeight()            { return netWeight; }
    public void setNetWeight(BigDecimal v)      { this.netWeight = v; }
    public BigDecimal getGrossWeight()          { return grossWeight; }
    public void setGrossWeight(BigDecimal v)    { this.grossWeight = v; }
    public BigDecimal getCalcNetWeight()        { return calcNetWeight; }
    public void setCalcNetWeight(BigDecimal v)  { this.calcNetWeight = v; }
    public BigDecimal getCalcGrossWeight()      { return calcGrossWeight; }
    public void setCalcGrossWeight(BigDecimal v){ this.calcGrossWeight = v; }
    public String getAmountInWords()            { return amountInWords; }
    public void setAmountInWords(String v)      { this.amountInWords = v; }
    public boolean isPartialShipment()          { return Boolean.TRUE.equals(partialShipment); }
    public void setPartialShipment(Boolean v)   { this.partialShipment = v == null || v; }
    public boolean isBtmaCertificate()          { return Boolean.TRUE.equals(btmaCertificate); }
    public void setBtmaCertificate(Boolean v)   { this.btmaCertificate = Boolean.TRUE.equals(v); }
    public LocalDate getAcknowledgedOn()        { return acknowledgedOn; }
    public void setAcknowledgedOn(LocalDate v)  { this.acknowledgedOn = v; }
    public CiKind getCiKind()                   { return ciKind; }
    public void setCiKind(CiKind v)             { this.ciKind = v; }
    public String getIbcNo()                    { return ibcNo; }
    public void setIbcNo(String v)              { this.ibcNo = v; }
    public RealizationStep getRealizationStep() { return realizationStep; }
    public void setRealizationStep(RealizationStep v) { this.realizationStep = v; }
    public ImportDocType getImportDocType()     { return importDocType; }
    public void setImportDocType(ImportDocType v) { this.importDocType = v; }
    public LcType getLcType()                   { return lcType; }
    public void setLcType(LcType v)             { this.lcType = v; }
    public String getPort()                     { return port; }
    public void setPort(String v)               { this.port = v; }
    public String getCnfAgent()                 { return cnfAgent; }
    public void setCnfAgent(String v)           { this.cnfAgent = v; }
    public String getIpNo()                     { return ipNo; }
    public void setIpNo(String v)               { this.ipNo = v; }
    public boolean isSroBenefited()             { return Boolean.TRUE.equals(sroBenefited); }
    public void setSroBenefited(Boolean v)      { this.sroBenefited = Boolean.TRUE.equals(v); }
    public String getBtmaNo()                   { return btmaNo; }
    public void setBtmaNo(String v)             { this.btmaNo = v; }
    public LocalDate getBtmaDate()              { return btmaDate; }
    public void setBtmaDate(LocalDate v)        { this.btmaDate = v; }
    public String getBillOfEntryNo()            { return billOfEntryNo; }
    public void setBillOfEntryNo(String v)      { this.billOfEntryNo = v; }
    public LocalDate getBillOfEntryDate()       { return billOfEntryDate; }
    public void setBillOfEntryDate(LocalDate v) { this.billOfEntryDate = v; }
    public Long getLocalAgentId()               { return localAgentId; }
    public void setLocalAgentId(Long v)         { this.localAgentId = v; }
    public Long getBackedByDocumentId()         { return backedByDocumentId; }
    public void setBackedByDocumentId(Long v)   { this.backedByDocumentId = v; }
    public BigDecimal getIncentiveAmount()      { return incentiveAmount; }
    public void setIncentiveAmount(BigDecimal v){ this.incentiveAmount = v; }
    public LocalDate getIncentiveAppliedOn()    { return incentiveAppliedOn; }
    public void setIncentiveAppliedOn(LocalDate v) { this.incentiveAppliedOn = v; }
    public LocalDate getIncentiveConfirmedOn()  { return incentiveConfirmedOn; }
    public void setIncentiveConfirmedOn(LocalDate v) { this.incentiveConfirmedOn = v; }
}
