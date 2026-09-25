package com.asg.fabricerp.marketing;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.utility.datatable.DataTableRequest;
import com.asg.fabricerp.utility.datatable.DataTableResponse;
import com.asg.fabricerp.utility.datatable.SortWhitelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marketing teams - ADM-4's master and its members. Approval is the team's matrix (Approval matrices).
 *
 * <pre>
 *   GET    /setup/marketing-teams                         page
 *   GET    /api/setup/marketing-teams                     grid rows
 *   GET    /api/setup/marketing-teams/{id}                one team, with its members
 *   POST   /api/setup/marketing-teams                     create or update
 *   DELETE /api/setup/marketing-teams/{id}                soft delete (refused while it owns anything)
 *   POST   /api/setup/marketing-teams/{id}/members        {userId, from}
 *   DELETE /api/setup/marketing-teams/{id}/members/{scope} ?reason=
 *   GET    /api/setup/marketing-teams/users               user picker for members
 *   GET    /api/marketing-teams                           active teams, for the Booking team picker
 * </pre>
 *
 * Members change who sees a team's documents, so adding one is AMEND on this screen, not CREATE.
 */
@Controller
public class MarketingTeamController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of(Map.of(
        "code",   "code",
        "name",   "name",
        "active", "active"
    ));

    private final MarketingTeamService service;
    private final MarketingTeamRepository teams;
    private final FabricUserRepository users;
    private final OrgContext context;

    public MarketingTeamController(MarketingTeamService service, MarketingTeamRepository teams,
                                   FabricUserRepository users, OrgContext context) {
        this.service = service;
        this.teams = teams;
        this.users = users;
        this.context = context;
    }

    public record MemberRequest(Long userId, LocalDate from) { }

    @GetMapping("/setup/marketing-teams")
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_VIEW')")
    public String page(Model model) {
        model.addAttribute("title", "Marketing teams");
        model.addAttribute("content", "setup/marketing-teams :: content");
        return "layout/main";
    }

    @GetMapping("/api/setup/marketing-teams")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_VIEW')")
    @Transactional(readOnly = true)
    public DataTableResponse<Map<String, Object>> grid(
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) Boolean active) {

        var request = new DataTableRequest(draw, start, length, search, sortColumn, sortDir);
        Page<MarketingTeam> page = service.search(request.searchOrNull(), active, request.toPageable(SORTABLE, "name"));
        return DataTableResponse.from(draw, page, this::row);
    }

    @GetMapping("/api/setup/marketing-teams/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_VIEW')")
    @Transactional(readOnly = true)
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> detail = row(service.get(id));
        detail.put("members", service.members(id));
        return detail;
    }

    @PostMapping("/api/setup/marketing-teams")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_CREATE') or hasAuthority('SCREEN_MARKETING_TEAM_AMEND')")
    public Map<String, Object> save(@RequestBody MarketingTeam team) {
        AuthorityChecks.require(team.getId() == null ? "SCREEN_MARKETING_TEAM_CREATE" : "SCREEN_MARKETING_TEAM_AMEND");
        return row(service.save(team));
    }

    @DeleteMapping("/api/setup/marketing-teams/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_DELETE')")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    @PostMapping("/api/setup/marketing-teams/{id}/members")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_AMEND')")
    public List<MarketingTeamService.Person> addMember(@PathVariable Long id, @RequestBody MemberRequest request) {
        service.addMember(id, request.userId(), request.from());
        return service.members(id);
    }

    @DeleteMapping("/api/setup/marketing-teams/{id}/members/{scopeId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_AMEND')")
    public List<MarketingTeamService.Person> removeMember(@PathVariable Long id, @PathVariable Long scopeId,
                                                         @RequestParam(required = false) String reason) {
        service.removeMember(id, scopeId, reason);
        return service.members(id);
    }

    /** The member picker: this organization's active users, marked unrestricted where they are. */
    @GetMapping("/api/setup/marketing-teams/users")
    @ResponseBody
    @PreAuthorize("hasAuthority('SCREEN_MARKETING_TEAM_AMEND')")
    @Transactional(readOnly = true)
    public LookupPage<LookupPage.Option> users(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) Long id) {
        Long orgId = context.requireOrganizationId();
        if (id != null) {
            return users.findScoped(id, orgId).map(u -> LookupPage.single(option(u)))
                .orElse(LookupPage.of(List.of(), false));
        }
        return LookupPage.of(users.search(orgId, q == null || q.isBlank() ? null : q.trim(), false, null, null,
            LookupPage.pageable(page, size, Sort.by("fullName"))), MarketingTeamController::option);
    }

    /**
     * Active teams for the Booking form's team picker. Open to anyone signed in, as asfl-erp's is:
     * a list of team names is not confidential - the documents a team owns are, and those are
     * scoped on their own.
     */
    @GetMapping("/api/marketing-teams")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lookup() {
        return teams.lookup(context.requireOrganizationId()).stream()
            .map(t -> Map.<String, Object>of("id", t.getId(), "code", t.getCode(), "text", t.getName()))
            .toList();
    }

    private Map<String, Object> row(MarketingTeam t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("code", t.getCode());
        row.put("name", t.getName());
        row.put("leaderUserId", t.getLeaderUserId());
        row.put("leaderName", service.userName(t.getLeaderUserId()));
        row.put("bookingTarget", t.getBookingTarget());
        row.put("remarks", t.getRemarks());
        row.put("active", t.getActive());
        row.put("memberCount", t.getId() == null ? 0 : service.memberCount(t.getId()));
        row.put("matrixCount", t.getId() == null ? 0 : service.matrixCount(t.getId()));
        return row;
    }

    private static LookupPage.Option option(FabricUser u) {
        String name = u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName();
        return new LookupPage.Option(u.getId(), u.getUsername(), name,
            u.isUnrestricted() ? u.getUsername() + " · unrestricted" : u.getUsername());
    }
}
