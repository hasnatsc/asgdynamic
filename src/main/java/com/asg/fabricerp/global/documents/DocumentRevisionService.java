package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.global.numbering.BusinessNumberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Raises revision n+1 of a document as a new row, leaving the committed original untouched.
 *
 * <p>Extracted rather than left on {@code BookingService} the moment a second document type
 * needed it: {@code BpoService} calls the same method. Every field this copies —
 * {@code partyId}, currency, dates, remarks, the fabric-specced groups and their colour
 * lines — lives on the generic {@link BusinessDocument} / {@link BusinessDocumentLineGroup}
 * / {@link BusinessDocumentColorLine}, so nothing here is Booking- or BPO-specific. A third
 * document type needs no changes to this class, only a call to it.
 */
@Service
public class DocumentRevisionService {

    private final BusinessDocumentRepository repository;
    private final BusinessNumberService numbering;

    public DocumentRevisionService(BusinessDocumentRepository repository, BusinessNumberService numbering) {
        this.repository = repository;
        this.numbering = numbering;
    }

    @Transactional
    public BusinessDocument revise(BusinessDocument original, String reason) {
        if (!original.getDocumentType().isRevisable()) {
            throw new IllegalStateException(
                "%s documents are not revisable; edit or reverse instead"
                    .formatted(original.getDocumentType().label()));
        }
        if (!original.getStatus().isCommitted()) {
            throw new IllegalStateException(
                "Only a committed document needs a revision; edit %s directly"
                    .formatted(original.getDocumentNo()));
        }

        BusinessDocument revision = new BusinessDocument();
        revision.setDocumentType(original.getDocumentType());
        revision.setOrganizationId(original.getOrganizationId());
        revision.setBusinessUnit(original.getBusinessUnit());
        revision.setWarehouse(original.getWarehouse());
        revision.stampMarketingTeam(original.getMarketingTeam());
        revision.setParty(original.getParty());
        revision.setCurrencyCode(original.getCurrencyCode());
        revision.setExchangeRate(original.getExchangeRate());
        revision.setDocumentDate(LocalDate.now());
        revision.setRequiredDate(original.getRequiredDate());
        revision.setReferenceNo(original.getReferenceNo());
        revision.setBookingType(original.getBookingType());
        revision.setOrderType(original.getOrderType());
        revision.setBrand(original.getBrand());
        revision.setGarments(original.getGarments());
        revision.setGarmentsAddress(original.getGarmentsAddress());
        revision.setPreCostBuyer(original.getPreCostBuyer());
        revision.setPriceInMeter(original.isPriceInMeter());
        revision.setMarketingPerson(original.getMarketingPerson());
        revision.setProcessKind(original.getProcessKind());
        revision.setVendor(original.getVendor());
        revision.setRemarks(reason);
        // Upstream link travels with the revision: a revised BPO still traces to the same
        // Booking. revisionOf is a different axis (previous version of THIS document) and
        // is set below, not here.
        revision.setParentDocument(original.getParentDocument());
        revision.setDocumentNo(numbering.next(original.getDocumentType(), revision.getDocumentDate(), revision.getBusinessUnit()));

        // Lineage always points at the root, so a chain never has to be walked to find it.
        revision.setRevisionOf(original.getRevisionOf() != null ? original.getRevisionOf() : original);
        revision.setRevisionNo(original.getRevisionNo() + 1);

        for (BusinessDocumentLineGroup group : original.getLineGroups()) {
            revision.addLineGroup(copyOf(group));
        }
        for (BusinessDocumentTerm term : original.getTerms()) {
            revision.addTerm(new BusinessDocumentTerm(term.getSerialNo(), term.getBodyText()));
        }
        revision.recalculateTotals();
        return repository.save(revision);
    }

    private BusinessDocumentLineGroup copyOf(BusinessDocumentLineGroup source) {
        BusinessDocumentLineGroup copy = new BusinessDocumentLineGroup();
        copy.setGroupNo(source.getGroupNo());
        copy.setItem(source.getItem());
        copy.setUom(source.getUom());
        copy.setFabric(copySpec(source.getFabric()));
        copy.setRoute(source.getRoute().copy());
        copy.setRevisedFromGroupId(source.getId());
        for (BusinessDocumentColorLine line : source.getColorLines()) {
            copy.addColorLine(copyOf(line));
        }
        return copy;
    }

    /**
     * {@code sourceColorLine} travels across the copy — the revision still traces to the
     * same upstream allocation. What does NOT happen here is a fresh {@code fulfil()} call
     * against that source: the original draw already recorded the consumption, and revising
     * the wording or price of a colour must not consume the source a second time. A revision
     * that changes <i>how much</i> was drawn is a deliberate reallocation, not implied by
     * raising a revision, and is out of scope until a document type actually needs it.
     */
    private BusinessDocumentColorLine copyOf(BusinessDocumentColorLine source) {
        BusinessDocumentColorLine copy = new BusinessDocumentColorLine();
        copy.setColorLineNo(source.getColorLineNo());
        copy.setSourceColorLine(source.getSourceColorLine());
        copy.setSourceLineGroup(source.getSourceLineGroup());
        copy.setRevisedFromLineId(source.getId());
        copy.setDeliveryDate(source.getDeliveryDate());
        copy.setFabricLotId(source.getFabricLotId());
        copy.setDyeLot(source.getDyeLot());
        copy.setShade(source.getShade());
        copy.setGrade(source.getGrade());
        copy.setRolls(source.getRolls());
        copy.setColorCode(source.getColorCode());
        copy.setColorName(source.getColorName());
        copy.setFabricsStyle(source.getFabricsStyle());
        copy.setColorReference(source.getColorReference());
        copy.setStrikeOffReference(source.getStrikeOffReference());
        copy.setLabDipReference(source.getLabDipReference());
        copy.setLoomReference(source.getLoomReference());
        copy.setColorSpecification(source.getColorSpecification());
        copy.setFilePath(source.getFilePath());
        copy.setQuantity(source.getQuantity());
        copy.setRate(source.getRate());
        copy.setPriceInMeter(source.getPriceInMeter());
        copy.setRemarks(source.getRemarks());
        // fulfilledQuantity is deliberately not copied: a revision starts unfulfilled
        // against its own row, even though its source line's ledger is untouched.
        return copy;
    }

    /**
     * An independent copy, not the same embeddable instance. {@code FabricSpec} is mutable,
     * and the previous version of this method did {@code copy.setFabric(source.getFabric())}
     * — sharing one object between the original and the revision in memory, so editing
     * either before both were flushed could silently corrupt the other. JPA copies an
     * embeddable's column values into each owning row regardless, but there is no reason to
     * carry that fragile aliasing through the Java object graph in the meantime.
     */
    /** A field-by-field copy of a fabric specification, for revisions and for documents raised against a line. */
    public static FabricSpec copySpec(FabricSpec source) {
        FabricSpec copy = new FabricSpec();
        copy.setConstruction(source.getConstruction());
        copy.setDeclaredConstruction(source.getDeclaredConstruction());
        copy.setWeaveType(source.getWeaveType());
        copy.setWeaveStyle(source.getWeaveStyle());
        copy.setFabricType(source.getFabricType());
        copy.setFinishType(source.getFinishType());
        copy.setComposition(source.getComposition());
        copy.setDeclaredComposition(source.getDeclaredComposition());
        copy.setWarpCount1(source.getWarpCount1());
        copy.setWarpCount2(source.getWarpCount2());
        copy.setWarpCount3(source.getWarpCount3());
        copy.setWarpCountRatio1(source.getWarpCountRatio1());
        copy.setWarpCountRatio2(source.getWarpCountRatio2());
        copy.setWarpCountRatio3(source.getWarpCountRatio3());
        copy.setWeftCount1(source.getWeftCount1());
        copy.setWeftCount2(source.getWeftCount2());
        copy.setWeftCount3(source.getWeftCount3());
        copy.setWeftCountRatio1(source.getWeftCountRatio1());
        copy.setWeftCountRatio2(source.getWeftCountRatio2());
        copy.setWeftCountRatio3(source.getWeftCountRatio3());
        copy.setEpi(source.getEpi());
        copy.setPpi(source.getPpi());
        copy.setShrinkageWarp(source.getShrinkageWarp());
        copy.setShrinkageWeft(source.getShrinkageWeft());
        copy.setShrinkageMechanical(source.getShrinkageMechanical());
        copy.setGsm(source.getGsm());
        copy.setGsmBeforeWash(source.getGsmBeforeWash());
        copy.setGsmAfterWash(source.getGsmAfterWash());
        copy.setFinishWidth(source.getFinishWidth());
        copy.setCuttableWidth(source.getCuttableWidth());
        copy.setLightSource(source.getLightSource());
        copy.setSelvedge(source.getSelvedge());
        copy.setWashType(source.getWashType());
        copy.setWashInstruction(source.getWashInstruction());
        copy.setEndUse(source.getEndUse());
        copy.setDispoReference(source.getDispoReference());
        copy.setCostingCode(source.getCostingCode());
        copy.setCostingAmendmentNo(source.getCostingAmendmentNo());
        copy.setFabricSource(source.getFabricSource());
        copy.setFinishTypeRef(source.getFinishTypeRef());
        copy.setQualityReference(source.getQualityReference());
        copy.setStyleReference(source.getStyleReference());
        copy.setQuotedPrice(source.getQuotedPrice());
        copy.setBreakEvenPrice(source.getBreakEvenPrice());
        copy.setWarpYarnName(source.getWarpYarnName());
        copy.setWeftYarnName(source.getWeftYarnName());
        copy.setLightSourceType(source.getLightSourceType());
        copy.setBaseMaterial(source.getBaseMaterial());
        copy.setSwatchNo(source.getSwatchNo());
        copy.setLcTenure(source.getLcTenure());
        copy.setLcPaymentType(source.getLcPaymentType());
        copy.setLeadTimeDays(source.getLeadTimeDays());
        copy.setTargetQualityParameter(source.getTargetQualityParameter());
        copy.setItemDescription(source.getItemDescription());
        return copy;
    }
}
