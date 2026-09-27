package com.asg.fabricerp.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class FabricUserDetailsService implements UserDetailsService {

    private final FabricUserRepository repository;
    private final DataScopeRepository scopes;
    private final WorkspaceResolver workspaces;

    public FabricUserDetailsService(FabricUserRepository repository, DataScopeRepository scopes,
                                    WorkspaceResolver workspaces) {
        this.repository = repository;
        this.scopes = scopes;
        this.workspaces = workspaces;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return reload(username, null)
            .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));
    }

    /**
     * Roles, scope and workspace resolved as of today, every time — used at login and again by
     * {@link SessionPrincipalRefreshFilter} on every request. Empty when the account has been
     * deleted since.
     *
     * @param chosen the workspace picked in the header this session, if any; honoured only while
     *               still permitted - see {@link WorkspaceResolver#resolve}
     */
    @Transactional(readOnly = true)
    public Optional<FabricUserPrincipal> reload(String username, WorkspaceSelection chosen) {
        LocalDate today = LocalDate.now();
        return repository.findByUsernameIgnoreCaseAndDeletedFalse(username).map(user -> {
            List<DataScope> grants = scopes.findByUserIdOrderByGrantedFromDesc(user.getId());
            Workspace workspace = workspaces.resolve(user,
                FabricUserPrincipal.organizationsOf(user, grants, today),
                FabricUserPrincipal.resolveScope(user, grants, today), chosen);
            return new FabricUserPrincipal(user, grants, today, workspace);
        });
    }
}
