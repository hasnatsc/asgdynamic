package com.asg.fabricerp.costing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The costing system could not answer. 502, and the message is shown to the user as-is: it
 * names the costing number and says what to check, which a generic server error would not.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class CostingUnavailableException extends RuntimeException {
    public CostingUnavailableException(String message) { super(message); }
    public CostingUnavailableException(String message, Throwable cause) { super(message, cause); }
}
