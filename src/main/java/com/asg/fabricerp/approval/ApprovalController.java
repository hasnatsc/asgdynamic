package com.asg.fabricerp.approval;

import com.asg.fabricerp.global.documents.BusinessDocument;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One approval workflow for every document type:
 *
 * <pre>
 *   POST /api/documents/{id}/submit
 *   POST /api/documents/{id}/approve?remarks=
 *   POST /api/documents/{id}/reject?remarks=
 *   GET  /api/documents/{id}/history
 * </pre>
 *
 * A future Rout Card or Work Order controller needs none of this repeated — it gets
 * submit/approve/reject/history for free the moment it is a {@link BusinessDocument}.
 *
 * <p>{@code isAuthenticated()} is the only static check here; the real, per-document-type
 * role requirement is resolved and checked inside {@link ApprovalService} because it is not
 * known until the document is loaded. See that class's javadoc for why.
 */
@RestController
@RequestMapping("/api/documents")
@PreAuthorize("isAuthenticated()")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id) {
        return toStatus(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id,
                                       @RequestParam(required = false) String remarks) {
        return toStatus(service.approve(id, remarks));
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id,
                                      @RequestParam(required = false) String remarks) {
        return toStatus(service.reject(id, remarks));
    }

    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(@PathVariable Long id) {
        return service.historyOf(id).stream().map(ApprovalController::toHistoryRow).toList();
    }

    private static Map<String, Object> toStatus(BusinessDocument doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId());
        row.put("documentNo", doc.getDocumentNo());
        row.put("status", doc.getStatus().name());
        return row;
    }

    private static Map<String, Object> toHistoryRow(ApprovalHistory h) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action", h.getAction().name());
        row.put("fromStatus", h.getFromStatus() == null ? null : h.getFromStatus().name());
        row.put("toStatus", h.getToStatus().name());
        row.put("remarks", h.getRemarks());
        row.put("actor", h.getCreatedBy());
        row.put("at", h.getCreatedAt() == null ? null : h.getCreatedAt().toString());
        return row;
    }
}
