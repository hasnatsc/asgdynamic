package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mechanics every "draw against a parent document's colours" document type shares:
 * BPO draws against Booking, Request-for-PI draws against BPO, a Weaving or Processing
 * Work Order draws against BPO, and so on down both the sales and production chains
 * asgdynamic encoded as separate screens per hop.
 *
 * <h2>Operates on colour lines, not fabric-spec groups</h2>
 * The drawable unit is a {@link BusinessDocumentColorLine} — a real production payload
 * confirmed a downstream document (a BPO, say) can commit less than a Booking line's full
 * colour breakdown, and the id it actually references ({@code so_line_dtl_id} in the legacy
 * JSON) is the colour line's, not the fabric-spec group's. {@link BusinessDocumentLineGroup}
 * only groups colours that share one construction; it has no quantity of its own to draw
 * against.
 *
 * <p>Extracted out of {@code BpoService} the moment a second consumer needed the identical
 * loadParent/draw/release logic — the same signal that produced
 * {@link DocumentRevisionService} and {@code com.asg.fabricerp.approval.ApprovalService}
 * before it. What is deliberately <b>not</b> here is document creation/update
 * orchestration: each document type has its own header fields worth copying and some carry
 * a costing refresh, some don't. Forcing one generic {@code create()}/{@code update()}
 * shape onto that variance would have been over-generalizing what was never actually
 * duplicated.
 */
@Service
public class ParentLineDrawService {

    private final BusinessDocumentRepository repository;
    private final OrgContext context;

    public ParentLineDrawService(BusinessDocumentRepository repository, OrgContext context) {
        this.repository = repository;
        this.context = context;
    }

    /** Loads a parent document, confirming it is the type the caller expects. */
    public BusinessDocument loadParent(Long parentId, DocumentType expectedType) {
        if (parentId == null) {
            throw new IllegalArgumentException(
                "Must name the %s this document is raised against".formatted(expectedType.label()));
        }
        BusinessDocument parent = repository.findScopedWithLines(parentId, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Parent document not found: " + parentId));
        if (parent.getDocumentType() != expectedType) {
            throw new IllegalArgumentException(
                "Document %s is not a %s".formatted(parent.getDocumentNo(), expectedType.label()));
        }
        return parent;
    }

    /** Parent colour lines still open to draw against — feeds a "raise against" line picker. */
    public List<BusinessDocumentColorLine> openLines(BusinessDocument parent) {
        return flatten(parent).stream()
            .filter(cl -> cl.outstandingQuantity().signum() > 0)
            .toList();
    }

    /**
     * Consumes parent colour-line capacity for every child colour line that names a source.
     *
     * <p>Numbering the child groups/colour lines used to happen here too, as a side effect
     * — which meant a document type with nothing to draw against (Booking) never got
     * numbered at all, since it never calls this method. Numbering now lives on
     * {@link BusinessDocument#renumberLines()}, called unconditionally from
     * {@code recalculateTotals()}, which every caller of this method already invokes
     * immediately afterward.
     */
    public void draw(BusinessDocument parent, List<BusinessDocumentLineGroup> childGroups) {
        Map<Long, BusinessDocumentColorLine> byId = indexById(flatten(parent));
        for (BusinessDocumentLineGroup group : childGroups) {
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                if (line.getSourceColorLineId() == null) continue;
                sourceLine(byId, line, parent).fulfil(line.getQuantity());
            }
        }
    }

    /** Gives back whatever a set of child colour lines had previously drawn. */
    public void release(BusinessDocument parent, List<BusinessDocumentLineGroup> childGroups) {
        Map<Long, BusinessDocumentColorLine> byId = indexById(flatten(parent));
        for (BusinessDocumentLineGroup group : childGroups) {
            for (BusinessDocumentColorLine line : group.getColorLines()) {
                if (line.getSourceColorLineId() == null) continue;
                sourceLine(byId, line, parent).release(line.getQuantity());
            }
        }
    }

    public void save(BusinessDocument parent) {
        repository.save(parent);
    }

    private BusinessDocumentColorLine sourceLine(Map<Long, BusinessDocumentColorLine> byId,
                                                 BusinessDocumentColorLine childLine,
                                                 BusinessDocument parent) {
        BusinessDocumentColorLine source = byId.get(childLine.getSourceColorLineId());
        if (source == null) {
            throw new IllegalArgumentException(
                "Colour line %d names source colour line %d, which is not on %s %s"
                    .formatted(childLine.getColorLineNo(), childLine.getSourceColorLineId(),
                              parent.getDocumentType().label(), parent.getDocumentNo()));
        }
        return source;
    }

    private List<BusinessDocumentColorLine> flatten(BusinessDocument doc) {
        List<BusinessDocumentColorLine> all = new ArrayList<>();
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            all.addAll(group.getColorLines());
        }
        return all;
    }

    private Map<Long, BusinessDocumentColorLine> indexById(List<BusinessDocumentColorLine> lines) {
        Map<Long, BusinessDocumentColorLine> byId = new HashMap<>();
        for (BusinessDocumentColorLine line : lines) byId.put(line.getId(), line);
        return byId;
    }
}
