package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.CiKind;
import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.terms.TermsConditionService;
import com.asg.fabricerp.inventory.item.HsCodeRepository;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.inventory.item.InventoryItemRepository;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.party.PartyBankAccount;
import com.asg.fabricerp.party.PartyRoleType;
import com.asg.fabricerp.party.PartyService;
import com.asg.fabricerp.supply.SupplyDraws;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asg.fabricerp.supply.SupplyDraws.lineName;

/**
 * Raising, editing, deleting and amending the commercial documents ({@link CommercialStep}).
 *
 * <p>A line is built from the parent line it names: fabric, colour, item, unit and price are
 * copied from it, and buyer or supplier, currency and marketing team are inherited from the parent
 * document - never typed - so an LC cannot open for another buyer than its PIs, nor a CI invoice at
 * another price than the LC. Each line then draws the parent line's balance and is refused, with the
 * exact over-quantity, past it. Recording against a document (UD, UP, costs, realization...) is
 * {@link CommercialRecordsService}; cancelling is {@link CommercialPostingService}.
 */
@Service
public class CommercialDocumentService {

    private final BusinessDocumentRepository repository;
    private final BusinessNumberService numbering;
    private final DocumentReferences references;
    private final DocumentRevisionService revisions;
    private final PartyService parties;
    private final HsCodeRepository hsCodes;
    private final InventoryItemRepository items;
    private final TermsConditionService terms;
    private final CommercialDetailsRepository detailsRepository;
    private final CommercialEventRepository events;
    private final CommercialDraws draws;
    private final OrgContext context;
    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;

    public CommercialDocumentService(BusinessDocumentRepository repository, BusinessNumberService numbering,
                                     DocumentReferences references, DocumentRevisionService revisions, PartyService parties,
                                     HsCodeRepository hsCodes, InventoryItemRepository items, TermsConditionService terms,
                                     CommercialDetailsRepository detailsRepository, CommercialEventRepository events,
                                     CommercialDraws draws, OrgContext context, EntityManager em, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.numbering = numbering;
        this.references = references;
        this.revisions = revisions;
        this.parties = parties;
        this.hsCodes = hsCodes;
        this.items = items;
        this.terms = terms;
        this.detailsRepository = detailsRepository;
        this.events = events;
        this.draws = draws;
        this.context = context;
        this.em = em;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(CommercialStep step, BusinessDocumentStatus status, LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), context.requireBusinessUnitId(),
            step.type(), status, from, to, query, context.requireRowScope(), pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(CommercialStep step, Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == step.type())
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException(step.label() + " not found: " + id));
    }

    /** A document's commercial facts; an empty row for one saved before it had any. */
    public CommercialDetails details(BusinessDocument doc) {
        return detailsRepository.findById(doc.getId()).orElseGet(() -> new CommercialDetails(doc.getId(), doc.getOrganizationId()));
    }

    /** The organization's own party - whose bank accounts are "ours" on every PI and LC. */
    public Long selfPartyId() {
        Long id = jdbc.queryForObject("SELECT self_party_id FROM org_organizations WHERE id = :org",
            new MapSqlParameterSource("org", context.requireOrganizationId()), Long.class);
        if (id == null) throw new IllegalStateException("The organization has no party of its own to hold its bank accounts");
        return id;
    }

    // ------------------------------------------------------------------------------------ save

    @Transactional
    public BusinessDocument save(CommercialStep step, CommercialDocumentRequest request) {
        if (request.lines().isEmpty()) {
            throw new IllegalArgumentException("Add at least one line to the " + step.label());
        }
        BusinessDocument doc;
        CiKind ciKind = request.details().ciKind() == null ? CiKind.REGULAR : request.details().ciKind();
        if (request.id() == null) {
            doc = new BusinessDocument();
            doc.setDocumentType(step.type());
            doc.setOrganizationId(context.requireOrganizationId());
            doc.setBusinessUnit(references.currentBusinessUnit());
        } else {
            doc = get(step, request.id());
            doc.assertEditable();
            if (draws.holdsDraws(doc)) draws.releaseAll(step, doc);
            if (step == CommercialStep.ECI) ciKind = details(doc).getCiKind() == null ? ciKind : details(doc).getCiKind();
        }

        List<Source> sources = resolve(step, request.lines(), ciKind);
        List<BusinessDocument> parentDocs = sources.stream().map(Source::parentDocument).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(BusinessDocument::getId, d -> d, (a, b) -> a, LinkedHashMap::new),
                m -> new ArrayList<>(m.values())));
        checkParents(step, parentDocs, sources);
        BusinessDocument parent = parentDocs.isEmpty() ? null : parentDocs.get(0);

        applyHeader(step, request, doc, parent);
        // An amendment's lines remember which line of the amended version each continues - by the
        // parent line they draw, which is unique per line - so its approval can hand over what
        // later documents hold of them.
        Map<String, Long> continues = new HashMap<>();
        for (BusinessDocumentColorLine l : SupplyDraws.lines(doc)) {
            if (l.getRevisedFromLineId() != null) continues.put(continuationKey(l), l.getRevisedFromLineId());
        }
        doc.setLineGroups(buildGroups(step, doc, sources));
        for (BusinessDocumentColorLine l : SupplyDraws.lines(doc)) {
            l.setRevisedFromLineId(continues.get(continuationKey(l)));
        }
        doc.recalculateTotals();
        if (request.terms() != null) {
            doc.setTerms(request.terms().stream().filter(t -> t != null && !t.isBlank())
                .map(t -> new BusinessDocumentTerm(0, t.strip())).toList());
            doc.normalizeTerms();
        } else if (doc.getId() == null) {
            doc.setTerms(terms.defaultTermsFor(step.conditionType()));
            doc.normalizeTerms();
        }
        if (doc.getDocumentNo() == null) {
            doc.setDocumentNo(numbering.next(step.type(), doc.getDocumentDate(), doc.getBusinessUnit()));
        }
        BusinessDocument saved = repository.saveAndFlush(doc);
        CommercialDetails details = details(saved);
        applyDetails(step, request.details(), ciKind, saved, details);
        detailsRepository.save(details);
        if (draws.holdsDraws(saved)) draws.drawAll(step, saved);
        return saved;
    }

    /** A request line with the parent line (and challan line, or item) it names, loaded and checked. */
    record Source(CommercialDocumentRequest.Line request, BusinessDocumentColorLine parentLine,
                  BusinessDocumentColorLine deliveryLine, InventoryItem item) {
        BusinessDocument parentDocument() {
            return parentLine == null ? null : parentLine.getLineGroup().getDocument();
        }
    }

    private List<Source> resolve(CommercialStep step, List<CommercialDocumentRequest.Line> lines, CiKind ciKind) {
        Long orgId = context.requireOrganizationId();
        List<Source> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CommercialDocumentRequest.Line l : lines) {
            BusinessDocumentColorLine parentLine = null, challan = null;
            InventoryItem item = null;
            if (l.sourceId() != null) {
                parentLine = em.find(BusinessDocumentColorLine.class, l.sourceId());
                BusinessDocument parent = parentLine == null ? null : parentLine.getLineGroup().getDocument();
                if (parent == null || !orgId.equals(parent.getOrganizationId()) || Boolean.TRUE.equals(parent.getDeleted())
                    || !parent.isVisibleTo(context.requireRowScope())) {
                    throw new IllegalArgumentException("Parent line not found: " + l.sourceId());
                }
                if (parent.getDocumentType() != step.parentType()) {
                    throw new IllegalArgumentException("%s is not a %s".formatted(parent.getDocumentNo(), step.parentType().label()));
                }
                if (!step.acceptsParentStatus(parent.getStatus())) {
                    throw new IllegalStateException("%s is %s: a %s is raised only against an approved, open %s"
                        .formatted(parent.getDocumentNo(), parent.getStatus().label().toLowerCase(), step.label(), step.parentType().label()));
                }
                if (parentLine.isShortClosed()) {
                    throw new IllegalStateException("%s on %s is short-closed; nothing more can be raised against it"
                        .formatted(lineName(parentLine), parent.getDocumentNo()));
                }
                if (!seen.add("S:" + l.sourceId()) && step != CommercialStep.ECI) {
                    throw new IllegalArgumentException("%s appears twice; put its whole quantity on one line".formatted(lineName(parentLine)));
                }
            } else if (step.allowsDirect()) {
                if (l.itemId() == null) throw new IllegalArgumentException("Choose the item for every line");
                item = items.findScoped(l.itemId(), orgId).orElseThrow(() -> new IllegalArgumentException("Item not found: " + l.itemId()));
                if (!Boolean.TRUE.equals(item.getActive())) throw new IllegalArgumentException(item.getName() + " is inactive");
                if (!seen.add("I:" + item.getId())) throw new IllegalArgumentException(item.getName() + " appears twice");
            } else {
                throw new IllegalArgumentException("Every line of a %s is raised against a %s line".formatted(step.label(), step.parentType().label()));
            }

            if (step == CommercialStep.ECI) {
                if (ciKind == CiKind.REGULAR) {
                    if (l.deliveryLineId() == null) {
                        throw new IllegalArgumentException("A regular CI invoices delivery challans: name the challan line for every line");
                    }
                    challan = challanLine(l.deliveryLineId());
                    if (!seen.add("D:" + challan.getId())) {
                        throw new IllegalArgumentException("Challan %s appears twice".formatted(challan.getLineGroup().getDocument().getDocumentNo()));
                    }
                    Long challanSchedule = scheduleLineOf(challan);
                    Long lcSchedule = parentLine.getSourceColorLine() == null ? null : parentLine.getSourceColorLine().getSourceColorLine() == null
                        ? null : parentLine.getSourceColorLine().getSourceColorLine().getId();
                    if (challanSchedule == null || !challanSchedule.equals(lcSchedule)) {
                        throw new IllegalArgumentException("Challan %s (%s) was not delivered against %s's PI line"
                            .formatted(challan.getLineGroup().getDocument().getDocumentNo(), lineName(challan),
                                parentLine.getLineGroup().getDocument().getDocumentNo()));
                    }
                } else if (l.deliveryLineId() != null) {
                    throw new IllegalArgumentException("An advance CI bills ahead of delivery; it names no challan");
                } else if (!seen.add("A:" + l.sourceId())) {
                    throw new IllegalArgumentException("%s appears twice".formatted(lineName(parentLine)));
                }
            }
            if (l.quantity() == null || l.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Give a quantity for %s".formatted(parentLine != null ? lineName(parentLine) : item.getName()));
            }
            out.add(new Source(l, parentLine, challan, item));
        }
        return out;
    }

    /** A posted delivery challan (Fabrics delivery) line of this organization. */
    private BusinessDocumentColorLine challanLine(Long id) {
        BusinessDocumentColorLine line = em.find(BusinessDocumentColorLine.class, id);
        BusinessDocument fd = line == null ? null : line.getLineGroup().getDocument();
        if (fd == null || fd.getDocumentType() != DocumentType.FABRICS_DELIVERY || !fd.getOrganizationId().equals(context.requireOrganizationId())
            || Boolean.TRUE.equals(fd.getDeleted()) || !fd.isVisibleTo(context.requireRowScope())) {
            throw new IllegalArgumentException("Delivery challan line not found: " + id);
        }
        if (fd.getStatus() != BusinessDocumentStatus.APPROVED && fd.getStatus() != BusinessDocumentStatus.COMPLETED) {
            throw new IllegalStateException("Challan %s is %s; only a posted delivery is invoiced".formatted(fd.getDocumentNo(),
                fd.getStatus().label().toLowerCase()));
        }
        return line;
    }

    /** The delivery schedule line a challan line was delivered against: challan → delivery order → schedule. */
    static Long scheduleLineOf(BusinessDocumentColorLine challanLine) {
        BusinessDocumentColorLine doLine = challanLine.getSourceColorLine();
        BusinessDocumentColorLine scheduleLine = doLine == null ? null : doLine.getSourceColorLine();
        return scheduleLine == null ? null : scheduleLine.getId();
    }

    /** One LC per CI and one SPR per import PI; several PIs or schedules only of one party, in one currency. */
    private void checkParents(CommercialStep step, List<BusinessDocument> parents, List<Source> sources) {
        if (parents.isEmpty()) return;
        if (sources.stream().anyMatch(s -> s.parentDocument() == null)) {
            throw new IllegalArgumentException("Lines raised against %s and items added directly cannot share a %s"
                .formatted(parents.get(0).getDocumentNo(), step.label()));
        }
        BusinessDocument first = parents.get(0);
        if (!step.manyParents() && parents.size() > 1) {
            throw new IllegalArgumentException("All lines must come from %s; %s is another %s"
                .formatted(first.getDocumentNo(), parents.get(1).getDocumentNo(), step.parentType().label()));
        }
        for (BusinessDocument p : parents) {
            if (!Objects.equals(AuditableEntity.idOf(p.getParty()), AuditableEntity.idOf(first.getParty()))) {
                throw new IllegalArgumentException("%s is for %s, %s for %s; one %s is for one party"
                    .formatted(p.getDocumentNo(), name(p.getParty()), first.getDocumentNo(), name(first.getParty()), step.label()));
            }
            if (!Objects.equals(p.getCurrencyCode(), first.getCurrencyCode())) {
                throw new IllegalArgumentException("%s is in %s and %s in %s; one %s is in one currency"
                    .formatted(p.getDocumentNo(), p.getCurrencyCode(), first.getDocumentNo(), first.getCurrencyCode(), step.label()));
            }
        }
    }

    private void applyHeader(CommercialStep step, CommercialDocumentRequest r, BusinessDocument doc, BusinessDocument parent) {
        boolean creating = doc.getId() == null;
        doc.setParentDocument(parent);
        doc.setDocumentDate(r.documentDate() != null ? r.documentDate()
            : doc.getDocumentDate() != null ? doc.getDocumentDate() : LocalDate.now());
        doc.setReferenceNo(blank(r.referenceNo()));
        doc.setRemarks(blank(r.remarks()));
        switch (step) {
            case EPI -> {
                // The applicant is whoever opens the LC: the garments factory the schedule delivers to, or the buyer.
                Party applicant = r.partyId() != null ? applicant(r.partyId())
                    : parent.getGarments() != null ? parent.getGarments() : parent.getParty();
                doc.setParty(applicant);
                doc.setBrand(parent.getBrand());
                doc.setGarments(parent.getGarments());
                doc.setMarketingPerson(parent.getMarketingPerson());
                if (creating) doc.stampMarketingTeam(parent.getMarketingTeam());
                inheritMoney(doc, parent, r);
            }
            case ELC, ECI, ILC -> {
                doc.setParty(parent.getParty());
                doc.setBrand(parent.getBrand());
                doc.setGarments(parent.getGarments());
                doc.setMarketingPerson(parent.getMarketingPerson());
                if (creating) doc.stampMarketingTeam(parent.getMarketingTeam());
                inheritMoney(doc, parent, r);
            }
            case IPI -> {
                doc.setParty(r.partyId() == null ? null : parties.requireHolder(r.partyId(), PartyRoleType.SUPPLIER));
                if (parent != null) doc.setWarehouse(parent.getWarehouse());
                String currency = r.currencyCode() == null || r.currencyCode().isBlank() ? "USD" : r.currencyCode().strip().toUpperCase(Locale.ROOT);
                if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency is a three-letter code, e.g. USD");
                doc.setCurrencyCode(currency);
                doc.setExchangeRate(rate(currency, r.exchangeRate()));
            }
        }
    }

    private void inheritMoney(BusinessDocument doc, BusinessDocument parent, CommercialDocumentRequest r) {
        doc.setCurrencyCode(parent.getCurrencyCode());
        doc.setPriceInMeter(parent.isPriceInMeter());
        doc.setExchangeRate(rate(parent.getCurrencyCode(), r.exchangeRate() != null ? r.exchangeRate() : parent.getExchangeRate()));
    }

    private static BigDecimal rate(String currency, BigDecimal given) {
        if ("BDT".equals(currency)) return BigDecimal.ONE;
        BigDecimal rate = given == null ? BigDecimal.ONE : given;
        if (rate.signum() <= 0) throw new IllegalArgumentException("Give the rate of " + currency + " to taka");
        return rate;
    }

    /** An export PI's applicant: a customer or a garments factory. */
    private Party applicant(Long id) {
        Party p = parties.require(id);
        if (!p.holds(PartyRoleType.CUSTOMER) && !p.holds(PartyRoleType.GARMENT_FACTORY)) {
            throw new IllegalArgumentException(p.getName() + " is neither a customer nor a garments factory");
        }
        return p;
    }

    private List<BusinessDocumentLineGroup> buildGroups(CommercialStep step, BusinessDocument doc, List<Source> sources) {
        Long orgId = context.requireOrganizationId();
        Map<Long, BusinessDocumentLineGroup> byParentGroup = new LinkedHashMap<>();
        List<BusinessDocumentLineGroup> groups = new ArrayList<>();
        for (Source s : sources) {
            CommercialDocumentRequest.Line r = s.request();
            BusinessDocumentColorLine line = new BusinessDocumentColorLine();
            BusinessDocumentLineGroup group;
            if (s.parentLine() != null) {
                BusinessDocumentLineGroup pg = s.parentLine().getLineGroup();
                boolean itemLine = step == CommercialStep.IPI || step == CommercialStep.ILC;
                group = itemLine ? null : byParentGroup.get(pg.getId());
                if (group == null) {
                    group = new BusinessDocumentLineGroup();
                    group.setOrganizationId(orgId);
                    group.setItem(pg.getItem());
                    group.setUom(pg.getUom());
                    group.setItemBrand(pg.getItemBrand());
                    group.setItemModel(pg.getItemModel());
                    group.setItemSpecification(pg.getItemSpecification());
                    group.setFabric(DocumentRevisionService.copySpec(pg.getFabric()));
                    if (!itemLine) byParentGroup.put(pg.getId(), group);
                    groups.add(group);
                }
                BusinessDocumentColorLine p = s.parentLine();
                line.setSourceColorLine(p);
                line.setColorName(p.getColorName());
                line.setColorCode(p.getColorCode());
                line.setFabricsStyle(p.getFabricsStyle());
                line.setColorReference(p.getColorReference());
                line.setLabDipReference(p.getLabDipReference());
                line.setDeliveryDate(p.getDeliveryDate());
                // The price travels: the schedule's to the PI, the PI's to the LC and the CI - except an
                // import PI raised from a requisition, which is where the supplier's price first appears.
                if (step == CommercialStep.IPI) {
                    line.setRate(price(r.rate(), lineName(p)));
                } else {
                    line.setRate(p.getRate());
                    line.setPriceInMeter(p.getPriceInMeter());
                }
                if (s.deliveryLine() != null) line.setDeliveryLine(s.deliveryLine());
            } else {
                group = new BusinessDocumentLineGroup();
                group.setOrganizationId(orgId);
                group.setItem(s.item());
                group.setUom(s.item().getBaseUnit());
                group.setItemBrand(s.item().getBrand());
                group.setItemModel(s.item().getModel());
                groups.add(group);
                line.setRate(price(r.rate(), s.item().getName()));
            }
            if (step == CommercialStep.IPI && r.specification() != null) group.setItemSpecification(blank(r.specification()));
            line.setQuantity(r.quantity());
            line.setRemarks(blank(r.remarks()));
            group.addColorLine(line);
        }
        return groups;
    }

    private static BigDecimal price(BigDecimal rate, String what) {
        if (rate == null) return BigDecimal.ZERO;
        if (rate.signum() < 0) throw new IllegalArgumentException("The price of %s cannot be negative".formatted(what));
        return rate;
    }

    /** The commercial facts, each checked: banks hold BANK, accounts are the right party's at the right bank. */
    private void applyDetails(CommercialStep step, CommercialDocumentRequest.Details d, CiKind ciKind, BusinessDocument doc,
                              CommercialDetails det) {
        Long orgId = context.requireOrganizationId();
        det.setValidityDate(d.validityDate());
        det.setShipmentDate(d.shipmentDate());
        if (d.shipmentDate() != null && d.validityDate() != null && d.shipmentDate().isAfter(d.validityDate())) {
            throw new IllegalArgumentException("The last shipment date is after the %s".formatted(step.isLc() ? "LC's expiry" : "validity"));
        }
        det.setTenure(d.tenure());
        det.setPaymentTerms(d.paymentTerms());
        det.setDeliveryTerms(d.deliveryTerms());
        det.setAmountInWords(AmountInWords.of(doc.getSubtotalAmount(), doc.getCurrencyCode()));

        // Our bank and account.
        Long selfId = selfPartyId();
        PartyBankAccount own = d.bankAccountId() == null ? null : account(d.bankAccountId(), selfId, "the company's own");
        Long bankId = d.bankId() != null ? parties.requireHolder(d.bankId(), PartyRoleType.BANK).getId()
            : own != null ? own.getBank().getId() : null;
        if (own != null && !own.getBank().getId().equals(bankId)) {
            throw new IllegalArgumentException("Account %s is at %s, not the bank chosen".formatted(own.getAccountNumber(), own.getBank().getName()));
        }
        det.setBankId(bankId);
        det.setBankAccountId(own == null ? null : own.getId());

        switch (step) {
            case EPI -> {
                det.setHsCodeId(d.hsCodeId() == null ? null : hsCodes.findScoped(d.hsCodeId(), orgId)
                    .orElseThrow(() -> new IllegalArgumentException("HS code not found: " + d.hsCodeId())).getId());
                det.setApplicantBondLicence(blank(d.applicantBondLicence()));
                weigh(doc, det, d);
            }
            case ELC -> {
                det.setLcNo(blank(d.lcNo()));
                det.setIssueDate(d.issueDate());
                det.setMasterLcNo(blank(d.masterLcNo()));
                det.setMasterLcDate(d.masterLcDate());
                det.setPartialShipment(d.partialShipment());
                det.setBtmaCertificate(d.btmaCertificate());
                counterBank(d, det, doc);
                det.setForeignBankName(blank(d.foreignBankName()));
                det.setForeignBankBin(blank(d.foreignBankBin()));
                det.setForeignBankSwift(blank(d.foreignBankSwift()));
                det.setForeignBankRouting(blank(d.foreignBankRouting()));
            }
            case ECI -> {
                det.setCiKind(ciKind);
                weigh(doc, det, d);
                // A CI is presented under its LC: the LC's number, banks, tenure and terms are its own,
                // so the bill matures, and its papers print, as the LC says.
                CommercialDetails lc = details(doc.getParentDocument());
                det.setLcNo(lc.getLcNo());
                det.setMasterLcNo(lc.getMasterLcNo());
                det.setMasterLcDate(lc.getMasterLcDate());
                det.setIssueDate(lc.getIssueDate());
                det.setTenure(lc.getTenure());
                det.setPaymentTerms(lc.getPaymentTerms());
                det.setDeliveryTerms(lc.getDeliveryTerms());
                det.setBankId(lc.getBankId());
                det.setBankAccountId(lc.getBankAccountId());
                det.setCounterBankId(lc.getCounterBankId());
                det.setCounterBankAccountId(lc.getCounterBankAccountId());
                det.setForeignBankName(lc.getForeignBankName());
                det.setForeignBankSwift(lc.getForeignBankSwift());
            }
            case IPI -> det.setLocalAgentId(d.localAgentId() == null ? null
                : parties.requireHolder(d.localAgentId(), PartyRoleType.AGENT).getId());
            case ILC -> {
                det.setImportDocType(d.importDocType() == null ? CommercialTerms.ImportDocType.LC : d.importDocType());
                det.setLcType(d.lcType());
                det.setLcNo(blank(d.lcNo()));
                det.setIssueDate(d.issueDate());
                det.setCnfAgent(blank(d.cnfAgent()));
                det.setIpNo(blank(d.ipNo()));
                det.setPort(blank(d.port()));
                det.setSroBenefited(d.sroBenefited());
                det.setBtmaNo(blank(d.btmaNo()));
                det.setBtmaDate(d.btmaDate());
                det.setPartialShipment(d.partialShipment());
                counterBank(d, det, doc);
                det.setForeignBankName(blank(d.foreignBankName()));
                det.setForeignBankSwift(blank(d.foreignBankSwift()));
                det.setBeneficiaryAccountNo(blank(d.beneficiaryAccountNo()));
                det.setBackedByDocumentId(d.backedByDocumentId() == null ? null : backingLc(d.backedByDocumentId()));
            }
        }
    }

    /** The other side's bank: the export LC buyer's bank, the import LC supplier's bank - and an account of theirs at it. */
    private void counterBank(CommercialDocumentRequest.Details d, CommercialDetails det, BusinessDocument doc) {
        Long partyId = AuditableEntity.idOf(doc.getParty());
        PartyBankAccount theirs = d.counterBankAccountId() == null ? null : account(d.counterBankAccountId(), partyId, name(doc.getParty()) + "'s");
        Long bankId = d.counterBankId() != null ? parties.requireHolder(d.counterBankId(), PartyRoleType.BANK).getId()
            : theirs != null ? theirs.getBank().getId() : null;
        if (theirs != null && !theirs.getBank().getId().equals(bankId)) {
            throw new IllegalArgumentException("Account %s is at %s, not the bank chosen".formatted(theirs.getAccountNumber(), theirs.getBank().getName()));
        }
        det.setCounterBankId(bankId);
        det.setCounterBankAccountId(theirs == null ? null : theirs.getId());
    }

    private PartyBankAccount account(Long id, Long ownerId, String whose) {
        PartyBankAccount a = em.find(PartyBankAccount.class, id);
        if (a == null || !context.requireOrganizationId().equals(a.getOrganizationId()) || !a.getParty().getId().equals(ownerId)) {
            throw new IllegalArgumentException("Bank account %s is not %s account".formatted(id, whose));
        }
        return a;
    }

    /** An import LC opened back-to-back against an approved export LC. */
    private Long backingLc(Long id) {
        BusinessDocument lc = repository.findScoped(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == DocumentType.EXPORT_LETTER_OF_CREDIT && !Boolean.TRUE.equals(d.getDeleted()))
            .orElseThrow(() -> new IllegalArgumentException("Export LC not found: " + id));
        if (!lc.getStatus().isCommitted()) throw new IllegalStateException(lc.getDocumentNo() + " is not an approved export LC");
        return lc.getId();
    }

    /** Calculated weights from the fabric; declared ones default to them and may not be lighter net than gross. */
    private static void weigh(BusinessDocument doc, CommercialDetails det, CommercialDocumentRequest.Details d) {
        FabricWeights.Result w = FabricWeights.of(doc.getLineGroups());
        det.setCalcNetWeight(w.net());
        det.setCalcGrossWeight(w.gross());
        BigDecimal net = d.netWeight() != null ? d.netWeight() : w.net();
        BigDecimal gross = d.grossWeight() != null ? d.grossWeight() : w.gross();
        if (net.signum() < 0 || gross.signum() < 0) throw new IllegalArgumentException("A weight cannot be negative");
        if (gross.compareTo(net) < 0) throw new IllegalArgumentException("The gross weight is less than the net weight");
        det.setNetWeight(net);
        det.setGrossWeight(gross);
    }

    // ---------------------------------------------------------------------------------- delete

    /** A draft or rejected document; what it drew is given back. */
    @Transactional
    public void delete(CommercialStep step, Long id) {
        BusinessDocument doc = get(step, id);
        doc.assertEditable();
        if (draws.holdsDraws(doc)) draws.releaseAll(step, doc);
        doc.markDeleted();
        repository.save(doc);
    }

    // -------------------------------------------------------------------------------- amending

    /** An amendment of an approved PI or LC: a new version, which takes over once approved. */
    @Transactional
    public BusinessDocument revise(CommercialStep step, Long id, String reason) {
        if (!step.isRevisable()) throw new IllegalStateException(step.plural() + " are not amended; cancel and raise again");
        BusinessDocument original = get(step, id);
        if (original.getStatus() == BusinessDocumentStatus.CLOSED) throw new IllegalStateException(original.getDocumentNo() + " is closed");
        Long rootId = original.getRevisionOf() != null ? original.getRevisionOf().getId() : original.getId();
        boolean pending = repository.revisionsOf(rootId, context.requireOrganizationId()).stream()
            .anyMatch(d -> d.getRevisionNo() > original.getRevisionNo() && !d.getStatus().isCommitted()
                && d.getStatus() != BusinessDocumentStatus.CANCELLED && !Boolean.TRUE.equals(d.getDeleted()));
        if (pending) throw new IllegalStateException("An amendment of %s is already in progress".formatted(original.getDocumentNo()));
        BusinessDocument revision = revisions.revise(original, reason);
        revision.setPriceInMeter(original.isPriceInMeter());
        repository.save(revision);
        detailsRepository.save(details(original).copyFor(revision.getId()));
        for (CommercialEvent e : events.findByDocumentIdOrderByEventDateAscIdAsc(original.getId())) {
            events.save(e.copyFor(revision.getId()));
        }
        return revision;
    }

    // ------------------------------------------------------------------------------- lookups

    /** Documents this step may be raised against: approved and open, in scope, newest first. */
    @Transactional(readOnly = true)
    public List<BusinessDocument> parents(CommercialStep step, String q, int page, int size) {
        String like = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        return em.createQuery("""
                select d from BusinessDocument d left join fetch d.party p
                where d.organizationId = :org and d.businessUnit.id = :unit and d.documentType = :type
                  and d.deleted = false and d.status in :statuses
                  and (lower(d.documentNo) like :q or lower(coalesce(d.referenceNo, '')) like :q or lower(coalesce(p.name, '')) like :q)
                order by d.documentDate desc, d.id desc
                """, BusinessDocument.class)
            .setParameter("org", context.requireOrganizationId())
            .setParameter("unit", context.requireBusinessUnitId())
            .setParameter("type", step.parentType())
            .setParameter("statuses", EnumSet.of(BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL))
            .setParameter("q", like)
            .setFirstResult(Math.max(0, page) * size)
            .setMaxResults(size + 1)
            .getResultList().stream()
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .toList();
    }

    /**
     * A parent's lines this step may still draw, with what each allows - and for a CI, the posted
     * delivery challans of its LC's PIs still to invoice. {@code excludeId}: the document being
     * edited, whose own draws count as available again.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> openLines(CommercialStep step, Long parentId, Long excludeId) {
        BusinessDocument parent = repository.findScopedWithLines(parentId, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == step.parentType())
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException(step.parentType().label() + " not found: " + parentId));
        Map<Long, BigDecimal> own = new HashMap<>();
        if (excludeId != null) {
            BusinessDocument editing = get(step, excludeId);
            if (draws.holdsDraws(editing)) {
                for (BusinessDocumentColorLine l : SupplyDraws.lines(editing)) {
                    if (l.getSourceColorLine() != null) own.merge(l.getSourceColorLine().getId(), SupplyDraws.held(l), BigDecimal::add);
                    if (l.getDeliveryLine() != null) own.merge(l.getDeliveryLine().getId(), SupplyDraws.held(l), BigDecimal::add);
                }
            }
        }
        List<BusinessDocumentColorLine> lines = SupplyDraws.lines(parent);
        Map<Long, Map<String, BigDecimal>> streams = draws.streams(parent);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, Long> lcLineBySchedule = new HashMap<>();
        for (BusinessDocumentColorLine l : lines) {
            if (step == CommercialStep.ECI && l.getSourceColorLine() != null && l.getSourceColorLine().getSourceColorLine() != null) {
                lcLineBySchedule.put(l.getSourceColorLine().getSourceColorLine().getId(), l.getId());
            }
            if (l.isShortClosed()) continue;
            BigDecimal taken = streams.getOrDefault(l.getId(), Map.of()).getOrDefault(step.stream(), BigDecimal.ZERO)
                .subtract(own.getOrDefault(l.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>(CommercialViews.lineOf(l));
            row.put("sourceId", l.getId());
            row.put("cap", l.getQuantity());
            row.put("taken", taken);
            row.put("available", l.getQuantity().subtract(taken).max(BigDecimal.ZERO));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parent", CommercialViews.header(parent));
        out.put("lines", rows);
        if (step == CommercialStep.ECI) out.put("challans", challans(lcLineBySchedule, own));
        return out;
    }

    /** Posted delivery challan lines against the LC's schedule lines, with what is left to invoice of each. */
    private List<Map<String, Object>> challans(Map<Long, Long> lcLineBySchedule, Map<Long, BigDecimal> own) {
        if (lcLineBySchedule.isEmpty()) return List.of();
        List<BusinessDocumentColorLine> found = em.createQuery("""
                select l from BusinessDocumentColorLine l join fetch l.lineGroup g join fetch g.document d
                  join fetch l.sourceColorLine dl
                where d.organizationId = :org and d.documentType = :fd and d.deleted = false and d.status in :posted
                  and dl.sourceColorLine.id in :schedule
                order by d.documentDate, d.id, g.groupNo, l.colorLineNo
                """, BusinessDocumentColorLine.class)
            .setParameter("org", context.requireOrganizationId())
            .setParameter("fd", DocumentType.FABRICS_DELIVERY)
            .setParameter("posted", EnumSet.of(BusinessDocumentStatus.APPROVED, BusinessDocumentStatus.COMPLETED))
            .setParameter("schedule", lcLineBySchedule.keySet())
            .getResultList();
        Map<Long, Map<String, BigDecimal>> invoiced = draws.ledger().streams(com.asg.fabricerp.production.LineDrawLedger.SourceKind.COLOUR,
            found.stream().map(BusinessDocumentColorLine::getId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (BusinessDocumentColorLine l : found) {
            BigDecimal done = invoiced.getOrDefault(l.getId(), Map.of()).getOrDefault(CommercialStep.ECI.stream(), BigDecimal.ZERO)
                .subtract(own.getOrDefault(l.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>(CommercialViews.lineOf(l));
            row.put("deliveryLineId", l.getId());
            row.put("challanNo", l.getLineGroup().getDocument().getDocumentNo());
            row.put("challanDate", l.getLineGroup().getDocument().getDocumentDate());
            row.put("lcLineId", lcLineBySchedule.get(scheduleLineOf(l)));
            row.put("invoiced", done);
            row.put("available", l.getQuantity().subtract(done).max(BigDecimal.ZERO));
            out.add(row);
        }
        return out;
    }

    private static String continuationKey(BusinessDocumentColorLine l) {
        if (l.getSourceColorLine() != null) return "S:" + l.getSourceColorLine().getId();
        InventoryItem item = l.getLineGroup().getItem();
        return item == null ? "L:" + l.getColorLineNo() : "I:" + item.getId();
    }

    static String name(Party p) {
        return p == null ? "no party" : p.getName();
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
