package com.asg.fabricerp.common;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByCodeIgnoreCase(String code);
}
