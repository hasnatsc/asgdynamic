package com.asg.fabricerp.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FabricUserDetailsService implements UserDetailsService {

    private final FabricUserRepository repository;

    public FabricUserDetailsService(FabricUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        FabricUser user = repository.findByUsernameIgnoreCaseAndDeletedFalse(username)
            .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));
        return new FabricUserPrincipal(user);
    }
}
