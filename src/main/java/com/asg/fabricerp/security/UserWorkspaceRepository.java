package com.asg.fabricerp.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserWorkspaceRepository extends JpaRepository<UserWorkspace, Long> {

    Optional<UserWorkspace> findByUserId(Long userId);
}
