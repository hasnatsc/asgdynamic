package com.asg.fabricerp.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataScopeRepository extends JpaRepository<DataScope, Long> {

    /** Every grant, open or closed — the principal filters to those held today. */
    List<DataScope> findByUserIdOrderByGrantedFromDesc(Long userId);
}
