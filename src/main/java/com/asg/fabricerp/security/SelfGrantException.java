package com.asg.fabricerp.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * An administrator changing their own access — asfl-erp's {@code SelfGrantException}.
 *
 * <p>Whoever holds {@code SECURITY_ADMIN} can already grant anything to anyone. What they must
 * not be able to do is do it quietly to themselves: widening your own roles or scope, or
 * clearing your own lock, is the one privilege escalation an administration screen makes
 * trivial, and refusing it is what makes a second administrator worth having. The four-eyes
 * rule {@code ApprovalService} applies to documents applies to the machinery that authorises
 * them most of all.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class SelfGrantException extends RuntimeException {

    public SelfGrantException(String what) {
        super("You cannot change " + what + " on your own account. Ask another administrator.");
    }
}
