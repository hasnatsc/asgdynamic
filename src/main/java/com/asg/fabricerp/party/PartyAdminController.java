package com.asg.fabricerp.party;

import com.asg.fabricerp.party.PartyAdminService.PartyRequest;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Party maintenance: the directory, the editor, and a CSV export of whatever the directory is
 * filtered to. Governed by the {@code PARTY} screen grant - unlike {@link PartyController}, the
 * read-only picker every document screen uses, which stays open to any signed-in user.
 *
 * <pre>
 *   GET    /setup/parties                  page
 *   GET    /api/setup/parties              grid rows (role, active, partyType filters)
 *   GET    /api/setup/parties/counts       filter-chip counts
 *   GET    /api/setup/parties/{id}         the full record
 *   POST   /api/setup/parties              create or update
 *   DELETE /api/setup/parties/{id}         soft delete, refused while in use
 *   GET    /api/setup/parties/export.csv   the filtered directory as CSV
 * </pre>
 */
@Controller
public class PartyAdminController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "code", "code",
        "name", "name",
        "partyType", "partyType",
        "active", "active",
        "updatedAt", "updatedAt"));

    /** Export page size: large enough to be few queries, small enough to hold in memory. */
    private static final int EXPORT_PAGE = 500;

    private final PartyAdminService service;

    public PartyAdminController(PartyAdminService service) {
        this.service = service;
    }

    @GetMapping("/setup/parties")
    @PreAuthorize("hasAuthority('SCREEN_PARTY_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Parties");
        model.addAttribute("roleTypes", PartyRoleType.values());
        model.addAttribute("addressTypes", PartyAddress.AddressType.values());
        model.addAttribute("content", "setup/parties :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/parties")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PARTY_VIEW')")
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) PartyRoleType role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Party.PartyType partyType) {
        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<Map<String, Object>> page = service.search(role, active, partyType, request.searchOrNull(),
            request.toPageable(SORTABLE, "code"));
        return DataTableResponse.from(draw, page, row -> row);
    }

    @GetMapping("/api/setup/parties/counts")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PARTY_VIEW')")
    public PartyAdminService.Counts counts() {
        return service.counts();
    }

    @GetMapping("/api/setup/parties/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PARTY_VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/setup/parties")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PARTY_CREATE') or hasAuthority('SCREEN_PARTY_AMEND')")
    public Map<String, Object> save(@RequestBody PartyRequest request) {
        AuthorityChecks.require(request.id() == null ? "SCREEN_PARTY_CREATE" : "SCREEN_PARTY_AMEND");
        return service.save(request);
    }

    @DeleteMapping("/api/setup/parties/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_PARTY_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    /**
     * Streams the filtered directory page by page, so an export of the whole directory never
     * holds it in memory. UTF-8 with a byte-order mark, which is what makes Excel read Bangla
     * names and the taka sign correctly.
     */
    @GetMapping("/api/setup/parties/export.csv")
    @PreAuthorize("hasAuthority('SCREEN_PARTY_VIEW')")
    public void export(@RequestParam(name = "search[value]", required = false) String search,
                       @RequestParam(required = false) PartyRoleType role,
                       @RequestParam(required = false) Boolean active,
                       @RequestParam(required = false) Party.PartyType partyType,
                       HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"parties-%s.csv\"".formatted(LocalDate.now()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        PrintWriter out = response.getWriter();
        out.write(0xFEFF);   // byte-order mark
        out.println(csv(List.of("Code", "Name", "Legal name", "Type", "Roles", "TIN", "BIN",
            "Primary contact", "Phone", "Email", "Location", "Status")));

        String q = search == null || search.isBlank() ? null : search.trim();
        int pageNo = 0;
        Page<Map<String, Object>> page;
        do {
            page = service.search(role, active, partyType, q,
                PageRequest.of(pageNo++, EXPORT_PAGE, Sort.by("code")));
            for (Map<String, Object> r : page.getContent()) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) r.get("roles");
                out.println(csv(List.of(
                    str(r.get("code")), str(r.get("name")), str(r.get("legalName")), str(r.get("partyType")),
                    String.join(" ", roles), str(r.get("tin")), str(r.get("bin")),
                    str(r.get("contactName")), str(r.get("contactPhone")), str(r.get("contactEmail")),
                    str(r.get("location")), Boolean.TRUE.equals(r.get("active")) ? "Active" : "Inactive")));
            }
            out.flush();
        } while (page.hasNext());
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * One CSV line. A value starting with = + - or @ is prefixed with an apostrophe, so a party
     * named "=HYPERLINK(...)" cannot run as a formula in whoever opens the export.
     */
    static String csv(List<String> values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
            if (i > 0) line.append(',');
            line.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return line.toString();
    }
}
