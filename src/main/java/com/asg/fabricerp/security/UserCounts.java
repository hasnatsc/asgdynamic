package com.asg.fabricerp.security;

/** The security overview's headline user numbers, filled by {@link FabricUserRepository#countSummary}. */
public record UserCounts(Long total, Long locked, Long mustChangePassword, Long unrestricted) { }
