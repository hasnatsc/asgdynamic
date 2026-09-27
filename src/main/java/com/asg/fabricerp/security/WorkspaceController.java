package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The header's workspace switcher: where the signed-in user works, among the organizations,
 * units, stores and cost centres an administrator has granted them. Any signed-in user, for
 * themselves only - there is no user id in these routes to point at somebody else.
 *
 * <p>A switch lasts for the session; {@code makeDefault} also saves it as where every later
 * session starts. Either way it is only a choice - {@link WorkspaceResolver} re-checks it on every
 * request, so it can never outlive the grants it was made under.
 */
@RestController
public class WorkspaceController {

    private final WorkspaceResolver workspaces;
    private final FabricUserRepository users;
    private final AccessLogService accessLog;

    public WorkspaceController(WorkspaceResolver workspaces, FabricUserRepository users, AccessLogService accessLog) {
        this.workspaces = workspaces;
        this.users = users;
        this.accessLog = accessLog;
    }

    @GetMapping("/api/workspace")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> current() {
        FabricUserPrincipal principal = principal();
        FabricUser user = user(principal);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("current", principal.getWorkspace().selection());
        body.put("savedDefault", workspaces.savedDefault(user.getId()));
        body.put("organizations", workspaces.options(user, principal));
        return body;
    }

    @PostMapping("/api/workspace")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> choose(@Valid @RequestBody ChooseRequest request, HttpSession session) {
        FabricUserPrincipal principal = principal();
        FabricUser user = user(principal);
        Workspace workspace = workspaces.require(user, principal, new WorkspaceSelection(
            request.organizationId(), request.businessUnitId(), request.warehouseId(), request.costCentreId()));

        session.setAttribute(WorkspaceSelection.SESSION_ATTRIBUTE, workspace.selection());
        if (request.makeDefault()) {
            workspaces.saveDefault(user.getId(), workspace);
        }
        accessLog.record(user.getId(), user.getUsername(), Event.WORKSPACE_CHANGED,
            "%s / %s".formatted(workspace.organizationCode(), workspace.businessUnitCode()),
            request.makeDefault() ? "Saved as default" : null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("current", workspace.selection());
        body.put("organizationName", workspace.organizationName());
        body.put("savedAsDefault", request.makeDefault());
        return body;
    }

    private static FabricUserPrincipal principal() {
        return CurrentUser.principal().orElseThrow(() -> new IllegalStateException("Nobody is signed in"));
    }

    private FabricUser user(FabricUserPrincipal principal) {
        return users.findById(principal.getUserId())
            .filter(u -> !Boolean.TRUE.equals(u.getDeleted()))
            .orElseThrow(() -> new IllegalStateException("Signed-in account no longer exists"));
    }

    public record ChooseRequest(@NotNull Long organizationId, @NotNull Long businessUnitId,
                                Long warehouseId, Long costCentreId, boolean makeDefault) { }
}
