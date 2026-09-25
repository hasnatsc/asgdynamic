package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.costing.CostingService;
import com.asg.fabricerp.global.documents.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Booking — the head of order-to-cash for woven fabric.
 *
 * <h2>What moved to the server</h2>
 * asgdynamic's Booking screen computed fabric cost, GSM and line amounts in the browser
 * ({@code onclick_so_dtlSet_fabricsCost}, {@code gsmCalculated}, {@code leadTime}) and
 * POSTed the results to {@code /booking/write}. Here the request carries intent — which
 * costing code, what quantity — and the server produces every number.
 *
 * <p>This class is the template for the other fabric document types. It stays small
 * because {@link BusinessDocument} owns the document rules and {@link CostingService} owns
 * the costing call; the deliberate contrast is SpindleERP's 4,709-line
 * {@code ManagementAnalyticsService}.
 */
@Service
public class BookingService {

    private static final DocumentType TYPE = DocumentType.BOOKING;

    private final BusinessDocumentRepository repository;
    private final DocumentNumberService numbering;
    private final CostingService costing;
    private final DocumentRevisionService revisions;
    private final DocumentReferences references;
    private final OrgContext context;

    public BookingService(BusinessDocumentRepository repository,
                          DocumentNumberService numbering,
                          CostingService costing,
                          DocumentRevisionService revisions,
                          DocumentReferences references,
                          OrgContext context) {
        this.repository = repository;
        this.numbering = numbering;
        this.costing = costing;
        this.revisions = revisions;
        this.references = references;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(BusinessDocumentStatus status,
                                         LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(
            context.requireOrganizationId(),
            context.requireBusinessUnitId(),
            TYPE, status, from, to, query, context.requireRowScope(), pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
    }

    /**
     * Create or update. Mirrors asgdynamic's single {@code /write} endpoint so the
     * front-end behaviour is unchanged, but the numbers are recomputed here every time.
     */
    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        references.resolve(submitted, TYPE);   // before anything is copied or flushed
        BusinessDocument target;

        if (submitted.getId() == null) {
            target = submitted;
            target.setDocumentType(TYPE);
            target.setOrganizationId(context.requireOrganizationId());
            target.setBusinessUnit(references.currentBusinessUnit());
            // ADM-7: a Booking is where the team is decided — the creator's own team, or none
            // for an unrestricted user. Every downstream document inherits it from here.
            target.stampMarketingTeam(references.marketingTeam(context.requireRowScope().soleMarketingTeam()));
            target.setDocumentNo(numbering.next(TYPE));
            if (target.getDocumentDate() == null) {
                target.setDocumentDate(LocalDate.now());
            }
        } else {
            target = get(submitted.getId());
            target.assertEditable();
            applyHeader(submitted, target);
            target.setLineGroups(submitted.getLineGroups());
        }

        refreshCostingFigures(target);
        target.recalculateTotals();
        return repository.save(target);
    }

    // submit/approve/reject live in ApprovalService now — generic over every document
    // type rather than a one-line copy per service. See BookingController.

    /**
     * Raises revision n+1 as a new document, leaving the approved original untouched.
     * asgdynamic modelled this as a separate {@code bookingRevision} controller and table;
     * the actual copy logic lives once in {@link DocumentRevisionService}, shared with
     * {@code BpoService}.
     */
    @Transactional
    public BusinessDocument revise(Long id, String reason) {
        return revisions.revise(get(id), reason);
    }

    @Transactional
    public void delete(Long id) {
        BusinessDocument doc = get(id);
        doc.assertEditable();
        doc.markDeleted();
        repository.save(doc);
    }

    private void applyHeader(BusinessDocument from, BusinessDocument to) {
        to.setParty(from.getParty());
        to.setDocumentDate(from.getDocumentDate());
        to.setRequiredDate(from.getRequiredDate());
        to.setCurrencyCode(from.getCurrencyCode());
        to.setExchangeRate(from.getExchangeRate());
        to.setReferenceNo(from.getReferenceNo());
        to.setRemarks(from.getRemarks());
        to.setWarehouse(from.getWarehouse());
    }

    /**
     * Pulls authoritative fabric figures for every group that names a costing code — the
     * costing lookup is per construction, not per colour — and uses the break-even price as
     * the rate for each of that group's colour lines that has not set its own.
     */
    private void refreshCostingFigures(BusinessDocument doc) {
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            if (!group.getFabric().hasCostingCode()) continue;

            BigDecimal breakEven = costing.applyTo(group.getFabric());
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                if (line.getRate().signum() == 0 && breakEven != null) {
                    line.setRate(breakEven);
                }
            }
        }
    }
}
