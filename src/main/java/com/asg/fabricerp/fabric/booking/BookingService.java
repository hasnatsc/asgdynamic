package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.approval.ApprovalRequest;
import com.asg.fabricerp.approval.ApprovalRequestRepository;
import com.asg.fabricerp.approval.ApprovalService;
import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.costing.CostingService;
import com.asg.fabricerp.costing.CostingTranslator;
import com.asg.fabricerp.costing.FabricCost;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.terms.ConditionType;
import com.asg.fabricerp.global.terms.TermsConditionService;
import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.DataScopeRepository;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Booking — the head of order-to-cash for woven fabric.
 *
 * <h2>What moved to the server</h2>
 * asgdynamic's Booking screen called the costing API from the browser - with the costing
 * credentials in the page source - and computed GSM, construction and line amounts there
 * ({@code onclick_so_dtlSet_fabricsCost}, {@code gsmCalculated}), POSTing the results to
 * {@code /booking/write}. Here the browser asks {@link #costingPrefill} for a ready-made
 * specification, and on save the server re-reads the costing and produces every number.
 *
 * <p>This class is the template for the other fabric document types. It stays small
 * because {@link BusinessDocument} owns the document rules, {@link CostingTranslator} owns the
 * costing mapping and {@link TermsConditionService} owns the standard clauses.
 */
@Service
public class BookingService {

    private static final DocumentType TYPE = DocumentType.BOOKING;

    private final BusinessDocumentRepository repository;
    private final BusinessNumberService numbering;
    private final CostingService costing;
    private final CostingTranslator translator;
    private final DocumentRevisionService revisions;
    private final DocumentReferences references;
    private final TermsConditionService terms;
    private final FabricUserRepository users;
    private final OrgContext context;
    private final MarketingTeamRepository marketingTeams;
    private final ApprovalRequestRepository approvals;
    private final DataScopeRepository scopes;
    private final ApprovalService approvalService;

    public BookingService(BusinessDocumentRepository repository,
                          BusinessNumberService numbering,
                          CostingService costing,
                          CostingTranslator translator,
                          DocumentRevisionService revisions,
                          DocumentReferences references,
                          TermsConditionService terms,
                          FabricUserRepository users,
                          OrgContext context,
                          MarketingTeamRepository marketingTeams,
                          ApprovalRequestRepository approvals,
                          DataScopeRepository scopes,
                          ApprovalService approvalService) {
        this.repository = repository;
        this.numbering = numbering;
        this.costing = costing;
        this.translator = translator;
        this.revisions = revisions;
        this.references = references;
        this.terms = terms;
        this.users = users;
        this.context = context;
        this.marketingTeams = marketingTeams;
        this.approvals = approvals;
        this.scopes = scopes;
        this.approvalService = approvalService;
    }

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(BusinessDocumentStatus status,
                                         LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(
            context.requireOrganizationId(),
            context.requireBusinessUnitId(),
            TYPE, status, from, to, query, context.requireRowScope(), currentUsername(), pageable);
    }

    /** The grid's rows, mapped inside the transaction because they name the buyer and garments. */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> searchRows(BusinessDocumentStatus status,
                                                LocalDate from, LocalDate to,
                                                String query, Pageable pageable) {
        Page<BusinessDocument> page = search(status, from, to, query, pageable);
        // Where each submitted booking has got to in approval: one query for the page.
        Map<Long, ApprovalRequest> live = page.isEmpty() ? Map.of()
            : approvals.findByDocumentIdInAndPendingTrue(page.map(BusinessDocument::getId).getContent()).stream()
                .collect(java.util.stream.Collectors.toMap(ApprovalRequest::getDocumentId, r -> r, (a, b) -> a));
        return page.map(d -> {
            Map<String, Object> row = BookingView.row(d);
            ApprovalRequest request = live.get(d.getId());
            row.put("approvalProgress", request == null ? null
                : request.getTotalLevels() > 1 ? "Level %d of %d".formatted(request.getCurrentLevel(), request.getTotalLevels())
                : "Awaiting approval");
            return row;
        });
    }

    /**
     * The Marketing Person picker: this organization's active users. {@code id} labels a saved
     * value, as every {@code App.RemoteSelect} feed does.
     */
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> marketingPersons(String q, Integer page, Integer size, Long id) {
        Long orgId = context.requireOrganizationId();
        if (id != null) {
            FabricUser user = references.user(orgId, id);
            return LookupPage.single(option(user));
        }
        return LookupPage.of(users.search(orgId, q == null || q.isBlank() ? null : q.trim(), false, null, null,
                LookupPage.pageable(page, size, Sort.by("fullName"))),
            BookingService::option);
    }

    private static LookupPage.Option option(FabricUser u) {
        String name = u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName();
        return new LookupPage.Option(u.getId(), u.getUsername(), name, u.getUsername());
    }

    /**
     * The marketing team the signed-in user belongs to - their open {@code MARKETING_TEAM}
     * data-scope grant, the same membership the Marketing teams screen maintains. Null when they
     * are in no team, or (which the one-open-grant constraint should prevent) in more than one.
     *
     * <p>A Booking is always filed under its creator's own team: there is no choice to make, and a
     * user in no team cannot raise one at all.
     */
    @Transactional(readOnly = true)
    public MarketingTeam ownTeam() {
        Set<Long> ids = memberTeamIds();
        return ids.size() == 1
            ? marketingTeams.findScoped(ids.iterator().next(), context.requireOrganizationId()).orElse(null)
            : null;
    }

    /** Whether the signed-in user may raise a Booking: they must belong to exactly one marketing team. */
    @Transactional(readOnly = true)
    public boolean canCreate() {
        return memberTeamIds().size() == 1;
    }

    private Set<Long> memberTeamIds() {
        RowScope scope = context.requireRowScope();
        if (scope.restricts(ScopeDimension.MARKETING_TEAM)) {
            return scope.allowedOn(ScopeDimension.MARKETING_TEAM);
        }
        // An unrestricted user's principal carries no scope values; ask the grants directly.
        return Set.copyOf(scopes.findValuesHeldOn(CurrentUser.id(), ScopeDimension.MARKETING_TEAM, LocalDate.now()));
    }

    private MarketingTeam requireOwnTeam() {
        Set<Long> ids = memberTeamIds();
        if (ids.isEmpty()) {
            throw new IllegalStateException("You are not in a marketing team, so you cannot create a booking. "
                + "Ask an administrator to add you to a team under Setup › Marketing teams.");
        }
        if (ids.size() > 1) {
            throw new IllegalStateException("You belong to more than one marketing team, so a booking cannot be "
                + "filed under one. Ask an administrator to put you in exactly one team.");
        }
        return references.marketingTeam(ids.iterator().next());
    }

    /** The username the audit listener stamps into {@code created_by}: a booking's owner. */
    private String currentUsername() {
        String username = context.username();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("No signed-in user");
        }
        return username;
    }

    private boolean ownedByCurrentUser(BusinessDocument d) {
        return Objects.equals(d.getCreatedBy(), currentUsername());
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .filter(this::ownedByCurrentUser)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
    }

    /**
     * Read-only access for the review drawer. The owner, as {@link #get}; and the booking's own
     * approvers - whoever its current level waits for (a team's bookings wait for that team's
     * approvers) or has already signed a level - because the Approvals inbox opens a booking here
     * to decide on it. Nobody else, however senior. Editing, deleting and revising stay with the
     * owner alone.
     */
    @Transactional(readOnly = true)
    public BusinessDocument view(Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == TYPE)
            .filter(d -> (d.isVisibleTo(context.requireRowScope()) && ownedByCurrentUser(d)) || approvalService.canReview(d))
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
    }

    /**
     * The full booking as the editor and the review drawer show it. Built inside the
     * transaction: the buyer, brand and marketing person names are lazy associations.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        BusinessDocument doc = view(id);
        Map<String, Object> detail = BookingView.detail(doc);
        if (!ownedByCurrentUser(doc)) {
            // An approver reviewing someone else's booking: no Edit, no Submit, no Raise revision.
            detail.put("editable", false);
            detail.put("submittable", false);
            detail.put("revisable", false);
        }
        return detail;
    }

    /**
     * What entering a costing number fills in: the specification, the colour plan priced at the
     * quoted price, the pre-cost buyer - and what the user should know before relying on it.
     *
     * @param bookingId the booking being edited, so it does not warn about itself; null when new
     */
    @Transactional(readOnly = true)
    public CostingPrefill costingPrefill(String code, Long bookingId) {
        FabricCost cost = costing.fetch(code == null ? null : code.trim());
        FabricSpec spec = translator.toSpec(cost);

        List<CostingPrefill.Colour> colours = translator.colours(cost).stream()
            .map(c -> new CostingPrefill.Colour(c.colour(), c.qty(), cost.piMarkup()))
            .toList();

        List<String> warnings = new ArrayList<>();
        if (!cost.isFinallyApproved()) {
            warnings.add("Costing %s is not finally approved yet (final approval: %s)."
                .formatted(cost.code(), cost.finalApproval() == null ? "pending" : cost.finalApproval()));
        }
        List<String> already = repository.documentNosQuotingCosting(
            context.requireOrganizationId(), TYPE, cost.code(), bookingId);
        if (!already.isEmpty()) {
            warnings.add("Costing %s is already booked on %s.".formatted(cost.code(), String.join(", ", already)));
        }

        return new CostingPrefill(
            cost.code(), trim(cost.buyerName()), trim(cost.referenceNo()), cost.orderQty(),
            cost.breakEvenPriceMtr(), trim(cost.note()),
            BookingView.spec(spec), colours, warnings);
    }

    /**
     * Create or update. Mirrors asgdynamic's single {@code /write} endpoint so the
     * front-end behaviour is unchanged, but the numbers are recomputed here every time.
     */
    @Transactional
    public BusinessDocument save(BusinessDocument submitted) {
        // Marketing person and team are decided here, not by the request.
        submitted.setMarketingPersonId(null);
        submitted.setMarketingPerson(null);
        submitted.setMarketingTeamId(null);
        references.resolve(submitted, TYPE);   // before anything is copied or flushed
        BusinessDocument target;

        if (submitted.getId() == null) {
            target = submitted;
            target.setDocumentType(TYPE);
            target.setOrganizationId(context.requireOrganizationId());
            target.setBusinessUnit(references.currentBusinessUnit());
            // ADM-7: a Booking is where the team is decided, once. Every downstream document
            // inherits it from here, and no later save can move it. It is always the creator's
            // own team - a user in no marketing team cannot raise a booking.
            target.stampMarketingTeam(requireOwnTeam());
            if (target.getDocumentDate() == null) {
                target.setDocumentDate(LocalDate.now());
            }
            if (target.getBookingType() == null) {
                target.setBookingType(BookingType.BULK);
            }
            // The marketing person is whoever creates the booking - set here, never chosen on the page.
            target.setMarketingPerson(references.user(context.requireOrganizationId(), CurrentUser.id()));
            if (!target.isTermsSubmitted()) {
                terms.defaultTermsFor(ConditionType.BOOKING).forEach(target::addTerm);
            }
            target.setDocumentNo(numbering.next(TYPE, target.getDocumentDate(), target.getBusinessUnit()));
        } else {
            target = get(submitted.getId());
            target.assertEditable();
            if (target.getStatus() == BusinessDocumentStatus.REJECTED) {
                // Corrected after a Reject: it is a draft again, to be submitted afresh.
                target.transitionTo(BusinessDocumentStatus.DRAFT);
            }
            applyHeader(submitted, target);
            target.setLineGroups(submitted.getLineGroups());
            if (submitted.isTermsSubmitted()) {
                target.setTerms(new ArrayList<>(submitted.getTerms()));
            }
        }

        validateHeader(target);
        for (BusinessDocumentLineGroup group : target.getLineGroups()) {
            refreshCostingFigures(group);
            completeSpec(group);
        }
        target.normalizeTerms();
        target.recalculateTotals();
        validateLines(target);
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
        to.setBookingType(from.getBookingType() == null ? to.getBookingType() : from.getBookingType());
        to.setOrderType(from.getOrderType());
        to.setBrand(from.getBrand());
        to.setGarments(from.getGarments());
        to.setGarmentsAddress(from.getGarmentsAddress());
        to.setPreCostBuyer(from.getPreCostBuyer());
        to.setPriceInMeter(from.isPriceInMeter());
        // Marketing person is not taken from the request: it stays the booking's creator.
    }

    /**
     * Pulls authoritative figures for a group that names a costing code - the costing lookup
     * is per construction, not per colour - and prices each colour line left at zero at the
     * costing's quoted price (the legacy "Quot Price Costing"), never at break-even, which
     * would book the order at no margin.
     *
     * <p>A group with no costing code cannot carry costing figures: whatever the request said
     * about quoted price, break-even or amendment is dropped rather than stored as if the
     * costing system had said it.
     */
    private void refreshCostingFigures(BusinessDocumentLineGroup group) {
        FabricSpec spec = group.getFabric();
        if (!spec.hasCostingCode()) {
            spec.setQuotedPrice(null);
            spec.setBreakEvenPrice(null);
            spec.setCostingAmendmentNo(null);
            return;
        }
        costing.applyTo(spec);
        BigDecimal quoted = spec.getQuotedPrice();
        if (quoted == null) return;
        for (BusinessDocumentColorLine line : group.getColorLines()) {
            if (line.getRate().signum() == 0 && (line.getPriceInMeter() == null || line.getPriceInMeter().signum() == 0)) {
                line.setRate(quoted);
            }
        }
    }

    /** The derived parts of a specification a user never types: construction, unit. */
    private static void completeSpec(BusinessDocumentLineGroup group) {
        FabricSpec spec = group.getFabric();
        if (isBlank(spec.getConstruction())) {
            spec.setConstruction(BookingView.construction(spec));
        }
        if (group.getUom() == null && group.getItem() != null) {
            group.setUom(group.getItem().getBaseUnit());
        }
    }

    private static void validateHeader(BusinessDocument doc) {
        if (doc.getParty() == null) {
            throw new IllegalArgumentException("Choose the buyer.");
        }
        if (doc.getRequiredDate() == null) {
            throw new IllegalArgumentException("Enter the delivery required date.");
        }
        if (doc.getRequiredDate().isBefore(doc.getDocumentDate())) {
            throw new IllegalArgumentException("Delivery required date %s is before the booking date %s."
                .formatted(doc.getRequiredDate(), doc.getDocumentDate()));
        }
    }

    /**
     * A draft may have no specifications yet; a specification may not have no colours, and a
     * colour may not be booked at nothing - the legacy form required quantity and price on
     * every colour line too. A single-colour fabric type takes exactly one colour per line.
     */
    private static void validateLines(BusinessDocument doc) {
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            String name = "Specification %d (%s)".formatted(group.getGroupNo(),
                isBlank(group.getFabric().getConstruction()) ? "no construction" : group.getFabric().getConstruction());
            if (group.getColorLines().isEmpty()) {
                throw new IllegalArgumentException(name + " has no colours. Add at least one colour line.");
            }
            String fabricType = group.getFabric().getFabricType();
            if (ColourStructure.of(fabricType) == ColourStructure.SINGLE && group.getColorLines().size() > 1) {
                throw new IllegalArgumentException(
                    "%s: %s is a single-colour fabric and takes one colour per line, not %d. Book each further colour as a line of its own."
                        .formatted(name, fabricType, group.getColorLines().size()));
            }
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                String colour = isBlank(line.getColorName()) ? "colour " + line.getColorLineNo() : line.getColorName();
                if (line.getQuantity().signum() <= 0) {
                    throw new IllegalArgumentException("%s: %s needs a quantity.".formatted(name, colour));
                }
                if (line.getRate().signum() <= 0) {
                    throw new IllegalArgumentException("%s: %s needs a price.".formatted(name, colour));
                }
            }
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
