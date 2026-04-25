package com.policyguard.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void append_savesEntityWithSuppliedFields() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> input = Map.of("prompt", "What is the policy?");
        Map<String, Object> output = Map.of("status", "answered");

        auditLogService.append("qry-001", "prompt_received", "system", input, output);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getQueryId()).isEqualTo("qry-001");
        assertThat(saved.getEventType()).isEqualTo("prompt_received");
        assertThat(saved.getActor()).isEqualTo("system");
        assertThat(saved.getInputData()).isEqualTo(input);
        assertThat(saved.getOutputData()).isEqualTo(output);
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void append_generatesLogIdWithPrefix() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.append("qry-002", "retrieval", "system", null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getLogId()).startsWith("log-");
        assertThat(captor.getValue().getLogId()).hasSizeGreaterThan(4);
    }

    /**
     * Asserts that {@link AuditLogService} exposes exactly one public business method
     * (the {@code append} method), proving the append-only contract.
     * Object-inherited methods (equals, hashCode, toString, getClass, wait, notify,
     * notifyAll) and Spring-generated proxy methods are excluded.
     */
    @Test
    void auditLogService_exposesExactlyOnePublicBusinessMethod() {
        Set<String> objectMethods = Arrays.stream(Object.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        long publicBusinessMethodCount = Arrays.stream(AuditLogService.class.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !objectMethods.contains(m.getName()))
                // exclude Spring-generated methods (e.g. $$_springCglib*)
                .filter(m -> !m.getName().startsWith("$$"))
                .count();

        assertThat(publicBusinessMethodCount)
                .as("AuditLogService should expose exactly 1 public business method (append)")
                .isEqualTo(1);
    }
}
