package com.asg.fabricerp.security;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FabricUserRepository extends JpaRepository<FabricUser, Long> {

    /** Authorities are @EAGER already, but the graph avoids a second round trip regardless. */
    @EntityGraph(attributePaths = "authorities")
    Optional<FabricUser> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
