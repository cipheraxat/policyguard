package com.policyguard.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.policyguard.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByDocumentId(String documentId);

    boolean existsByDocumentId(String documentId);
}
