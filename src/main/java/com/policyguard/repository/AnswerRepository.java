package com.policyguard.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.policyguard.domain.Answer;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findByQueryId(String queryId);
}
