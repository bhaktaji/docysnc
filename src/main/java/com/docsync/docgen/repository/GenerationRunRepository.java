package com.docsync.docgen.repository;

import com.docsync.docgen.entity.GenerationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GenerationRunRepository extends JpaRepository<GenerationRun, UUID> {
}
