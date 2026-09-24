package com.asg.fabricerp.security;

import com.asg.fabricerp.security.AccessLogEntry.Event;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** The signed-in user acting on their own account — the other half of {@link UserAdminService}. */
@Service
public class AccountService {

    private final FabricUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AccessLogService accessLog;

    public AccountService(FabricUserRepository repository, PasswordEncoder passwordEncoder,
                          AccessLogService accessLog) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.accessLog = accessLog;
    }

    /**
     * Changes your own password, proving the current one first. Without that proof a borrowed
     * session — a terminal left signed in — becomes a permanent credential.
     *
     * <p>Clears {@code mustChangePassword}: the new value is one only its owner knows.
     */
    @Transactional
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        FabricUser user = repository.findById(userId)
            .filter(u -> !Boolean.TRUE.equals(u.getDeleted()))
            .orElseThrow(() -> new IllegalStateException("Signed-in account no longer exists"));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must differ from the current one");
        }
        PasswordPolicy.requireAcceptable(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword), false, LocalDateTime.now());
        repository.save(user);
        accessLog.record(userId, user.getUsername(), Event.PASSWORD_CHANGED, "ACCOUNT", null);
    }
}
