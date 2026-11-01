package com.docsync.identity.repository;

import com.docsync.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGithubId(Long githubId);

    boolean existsByEmailIgnoreCase(String email);
}
