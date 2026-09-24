package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mechanics every "draw against a parent document's lines" document type shares:
 * BPO draws against Booking, Request-for-PI draws against BPO, a Weaving or Processing
 * Work Order draws against BPO, Greige Issue draws against Greige Receive, and so on down
 * both the sales and production chains asgdynamic encoded as separate screens per hop.
 *
 * <p>Extracted out of {@code BpoService} the moment a second consumer
 * ({@code RequestForPiService}) needed the identical loadParent/draw/release logic — the
 * same signal that produced {@link DocumentRevisionService} and
 * {@code com.asg.fabricerp.approval.ApprovalService} before it. What is deliberately
 * <b>not</b> here is document creation/update orchestration: each document type has its
 * own header fields worth copying (BPO copies remarks/dates but not currency; Booking
 * copies currency and reference number) and some carry a costing refresh, some don't
 * (neither Request-for-PI nor a Work Order does — see their service javadocs). Forcing one
 * generic {@code create()}/{@code update()} shape onto that variance would have been
 * over-generalizing what was never actually duplicated.
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

    /** Parent lines still open to draw against — feeds a "raise against" form's line picker. */
    public List<BusinessDocumentLine> openLines(BusinessDocument parent) {
        return parent.getLines().stream()
            .filter(l -> l.outstandingQuantity().signum() > 0)
            .toList();
    }

    /**
     * Consumes parent-line capacity for every child line that names a source, and numbers
     * the child lines 1..n while it is there (every caller needs this regardless).
     */
    public void draw(BusinessDocument parent, List<BusinessDocumentLine> childLines) {
        Map<Long, BusinessDocumentLine> byId = indexById(parent.getLines());
        int lineNo = 1;
        for (BusinessDocumentLine line : childLines) {
            line.setLineNo(lineNo++);
            if (line.getSourceLineId() == null) continue;
            sourceLine(byId, line, parent).fulfil(line.getQuantity());
        }
    }

    /** Gives back whatever a set of child lines had previously drawn. */
    public void release(BusinessDocument parent, List<BusinessDocumentLine> childLines) {
        Map<Long, BusinessDocumentLine> byId = indexById(parent.getLines());
        for (BusinessDocumentLine line : childLines) {
            if (line.getSourceLineId() == null) continue;
            sourceLine(byId, line, parent).release(line.getQuantity());
        }
    }

    public void save(BusinessDocument parent) {
        repository.save(parent);
    }

    private BusinessDocumentLine sourceLine(Map<Long, BusinessDocumentLine> byId,
                                            BusinessDocumentLine childLine, BusinessDocument parent) {
        BusinessDocumentLine source = byId.get(childLine.getSourceLineId());
        if (source == null) {
            throw new IllegalArgumentException(
                "Line %d names source line %d, which is not on %s %s"
                    .formatted(childLine.getLineNo(), childLine.getSourceLineId(),
                              parent.getDocumentType().label(), parent.getDocumentNo()));
        }
        return source;
    }

    private Map<Long, BusinessDocumentLine> indexById(List<BusinessDocumentLine> lines) {
        Map<Long, BusinessDocumentLine> byId = new HashMap<>();
        for (BusinessDocumentLine line : lines) byId.put(line.getId(), line);
        return byId;
    }
}
