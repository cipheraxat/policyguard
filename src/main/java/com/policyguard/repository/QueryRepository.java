package com.policyguard.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.policyguard.domain.Query;

public interface QueryRepository extends JpaRepository<Query, UUID> {

    Optional<Query> findByQueryId(String queryId);
}
