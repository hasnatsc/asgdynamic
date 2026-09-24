package com.asg.fabricerp.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessLogRepository extends JpaRepository<AccessLogEntry, Long> {
}
