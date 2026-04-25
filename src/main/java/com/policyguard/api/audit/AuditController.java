package com.policyguard.api.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.policyguard.api.audit.dto.AuditEventDto;
import com.policyguard.api.audit.dto.AuditLogResponse;
import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/{queryId}")
    public AuditLogResponse getAuditLog(@PathVariable String queryId) {
        List<AuditLog> logs = auditLogRepository.findByQueryIdOrderByTimestampAsc(queryId);

        List<AuditEventDto> events = logs.stream()
                .map(log -> new AuditEventDto(
                        log.getEventType(),
                        log.getTimestamp(),
                        log.getActor(),
                        log.getInputData(),
                        log.getOutputData()
                ))
                .toList();

        // Return 200 with empty list if no events (empty list is still useful for debugging)
        return new AuditLogResponse(queryId, events);
    }
}
