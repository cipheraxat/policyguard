package com.policyguard.service.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;

/**
 * Append-only audit log service.
 * <p>
 * This service intentionally exposes <strong>only one public business method</strong>:
 * {@link #append}. No update or delete operations are provided, enforcing the
 * append-only audit guarantee defined in the architecture.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Appends a single audit event. Runs in its own transaction so audit writes
     * survive outer-transaction rollbacks.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(String queryId, String eventType, String actor,
                       Map<String, Object> input, Map<String, Object> output) {
        String logId = "log-" + UUID.randomUUID();

        AuditLog entry = new AuditLog();
        entry.setLogId(logId);
        entry.setQueryId(queryId);
        entry.setEventType(eventType);
        entry.setActor(actor);
        entry.setInputData(input);
        entry.setOutputData(output);
        entry.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));

        auditLogRepository.save(entry);
    }
}
