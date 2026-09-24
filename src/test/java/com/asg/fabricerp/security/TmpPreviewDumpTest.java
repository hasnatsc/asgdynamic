package com.asg.fabricerp.security;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.AccessLogEntry.Event;
import com.asg.fabricerp.security.SecurityOverviewService.Overview;
import com.asg.fabricerp.security.SecurityOverviewService.RoleCounts;
import com.asg.fabricerp.web.HomeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** THROWAWAY: renders pages with sample data to target/ui-preview for a visual check. Delete after. */
@WebMvcTest(controllers = {HomeController.class, UserAdminController.class, RoleController.class,
                           SecurityOverviewController.class, AccessLogController.class})
@Import(SecurityConfig.class)
class TmpPreviewDumpTest {

    @Autowired MockMvc mvc;
    @MockitoBean FabricUserDetailsService userDetailsService;
    @MockitoBean AccessLogService accessLogService;
    @MockitoBean UserAdminService userAdminService;
    @MockitoBean RoleService roleService;
    @MockitoBean SecurityOverviewService overviewService;
    @MockitoBean RoleRepository roleRepository;
    @MockitoBean FabricUserRepository userRepository;
    @MockitoBean BusinessUnitRepository businessUnits;
    @MockitoBean WarehouseRepository warehouses;
    @MockitoBean MarketingTeamRepository marketingTeams;
    @MockitoBean OrgContext orgContext;

    long ids = 100;

    @Test
    void dump() throws Exception {
        FabricUser admin = user(1L, "admin", "Asha Rahman");
        Role secAdmin = role("Security Admin");
        secAdmin.grant(Screen.SECURITY_ADMIN, Verb.values());
        Role ops = role("Fabric Operations");
        for (Screen s : List.of(Screen.BOOKING, Screen.BPO, Screen.RPI, Screen.WWO, Screen.PWO, Screen.GR, Screen.DO, Screen.FD, Screen.FABRIC_SETUP)) {
            ops.grant(s, Verb.VIEW, Verb.CREATE);
        }
        admin.addRole(secAdmin);
        admin.addRole(ops);
        when(userDetailsService.reload("admin")).thenAnswer(i -> Optional.of(new FabricUserPrincipal(admin)));
        when(orgContext.requireOrganizationId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        BusinessUnit unit = new BusinessUnit("AF", "Weaving Unit");
        unit.setId(10L);
        when(businessUnits.findById(10L)).thenReturn(Optional.of(unit));
        when(businessUnits.lookup(1L)).thenReturn(List.of(unit));

        FabricUser locked = user(3L, "rahim.k", "Rahim Karim");
        locked.lock("Locked after 5 consecutive failed logins", LocalDateTime.now().minusHours(3));
        FabricUser noScope = user(4L, "nadia.s", "Nadia Sultana");
        List<AccessLogEntry> events = List.of(
            new AccessLogEntry(2L, "clerk", Event.ACCESS_DENIED, "UserAdminController.page", "AuthorizationDecision [granted=false]", "10.0.0.14", LocalDateTime.now().minusMinutes(12)),
            new AccessLogEntry(3L, "rahim.k", Event.ACCOUNT_LOCKED, "LOGIN", "Locked after 5 consecutive failed logins", "10.0.0.22", LocalDateTime.now().minusHours(3)),
            new AccessLogEntry(3L, "rahim.k", Event.LOGIN_FAILED, "LOGIN", "Wrong password", "10.0.0.22", LocalDateTime.now().minusHours(3)),
            new AccessLogEntry(1L, "admin", Event.PASSWORD_CHANGED, "ACCOUNT", null, "10.0.0.5", LocalDateTime.now().minusDays(1)));
        when(overviewService.overview()).thenReturn(new Overview(new UserCounts(38L, 1L, 4L, 6L), new RoleCounts(73, 73, 70),
            Map.of(Event.LOGIN_FAILED, 7L, Event.ACCOUNT_LOCKED, 1L, Event.ACCESS_DENIED, 3L),
            List.of(locked), 2, List.of(noScope), events));
        when(roleRepository.findAll(any(Sort.class))).thenReturn(List.of(ops, secAdmin));

        Path out = Path.of("target/ui-preview");
        Files.createDirectories(out);
        String css = Path.of("src/main/resources/static/css/app.css").toAbsolutePath().toUri().toString();
        String js = Path.of("src/main/resources/static/js/app.js").toAbsolutePath().toUri().toString();
        var principal = new FabricUserPrincipal(admin);
        var auth = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        for (String[] p : new String[][]{{"/", "home"}, {"/setup/security", "security"}, {"/setup/users", "users"},
                                         {"/setup/roles", "roles"}, {"/setup/access-log", "access-log"}}) {
            String html = mvc.perform(get(p[0]).with(auth)).andReturn().getResponse().getContentAsString();
            html = html.replace("href=\"/css/app.css\"", "href=\"" + css + "\"")
                       .replace("src=\"/js/app.js\"", "src=\"" + js + "\"");
            Files.writeString(out.resolve(p[1] + ".html"), html);
        }
    }

    private FabricUser user(Long id, String username, String fullName) {
        FabricUser user = new FabricUser(username, "hash", 10L, "AF");
        user.setId(id);
        user.setOrganizationId(1L);
        user.setFullName(fullName);
        user.setUnrestricted(true);
        return user;
    }

    private Role role(String name) {
        Role role = new Role(name, null);
        role.setId(ids++);
        return role;
    }
}
