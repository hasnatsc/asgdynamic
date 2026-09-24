package com.asg.fabricerp.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class FabricUserDetailsService implements UserDetailsService {

    private final FabricUserRepository repository;
    private final DataScopeRepository scopes;

    public FabricUserDetailsService(FabricUserRepository repository, DataScopeRepository scopes) {
        this.repository = repository;
        this.scopes = scopes;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return reload(username)
            .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));
    }

    /**
     * Roles and scope resolved as of today, every time — used at login and again by
     * {@link SessionPrincipalRefreshFilter} on every request. Empty when the account has been
     * deleted since.
     */
    @Transactional(readOnly = true)
    public Optional<FabricUserPrincipal> reload(String username) {
        return repository.findByUsernameIgnoreCaseAndDeletedFalse(username)
            .map(user -> new FabricUserPrincipal(user,
                scopes.findByUserIdOrderByGrantedFromDesc(user.getId()), LocalDate.now()));
    }
}
