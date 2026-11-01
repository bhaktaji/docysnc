package com.docsync.organization.repository;

import com.docsync.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);
}
