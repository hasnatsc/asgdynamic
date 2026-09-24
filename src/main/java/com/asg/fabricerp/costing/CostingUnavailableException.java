package com.asg.fabricerp.costing;

public class CostingUnavailableException extends RuntimeException {
    public CostingUnavailableException(String message) { super(message); }
    public CostingUnavailableException(String message, Throwable cause) { super(message, cause); }
}
