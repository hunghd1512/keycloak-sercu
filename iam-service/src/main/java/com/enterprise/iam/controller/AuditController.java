package com.enterprise.iam.controller;

import com.enterprise.iam.application.dto.*;
import com.enterprise.iam.domain.entity.AuditLog;
import com.enterprise.iam.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {
    
    private final AuditService auditService;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogs(
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<AuditLog> logs = auditService.getAuditLogs(pageable);
        
        PagedResponse<AuditLogDto> response = PagedResponse.of(
            logs.getContent().stream().map(this::toAuditLogDto).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/actor/{actorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogsByActor(
            @PathVariable String actorId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<AuditLog> logs = auditService.getAuditLogsByActor(actorId, pageable);
        
        PagedResponse<AuditLogDto> response = PagedResponse.of(
            logs.getContent().stream().map(this::toAuditLogDto).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/target/{targetType}/{targetId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogsByTarget(
            @PathVariable String targetType,
            @PathVariable String targetId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        AuditLog.TargetType type = AuditLog.TargetType.valueOf(targetType.toUpperCase());
        Page<AuditLog> logs = auditService.getAuditLogsByTarget(type, targetId, pageable);
        
        PagedResponse<AuditLogDto> response = PagedResponse.of(
            logs.getContent().stream().map(this::toAuditLogDto).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/event/{eventType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogsByEventType(
            @PathVariable String eventType,
            @PageableDefault(size = 20) Pageable pageable) {
        
        AuditLog.AuditEventType type = AuditLog.AuditEventType.valueOf(eventType.toUpperCase());
        Page<AuditLog> logs = auditService.getAuditLogsByEventType(type, pageable);
        
        PagedResponse<AuditLogDto> response = PagedResponse.of(
            logs.getContent().stream().map(this::toAuditLogDto).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<AuditLog> logs = auditService.getAuditLogsByDateRange(startDate, endDate, pageable);
        
        PagedResponse<AuditLogDto> response = PagedResponse.of(
            logs.getContent().stream().map(this::toAuditLogDto).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getFailedEvents(
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<AuditLog> logs = auditService.getFailedEvents(pageable);
        
        PagedResponse<AuditLogDto> response = PagedResponse.of(
            logs.getContent().stream().map(this::toAuditLogDto).toList(),
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    private AuditLogDto toAuditLogDto(AuditLog log) {
        return AuditLogDto.builder()
            .id(log.getId())
            .eventType(log.getEventType().name())
            .actorId(log.getActorId())
            .actorUsername(log.getActorUsername())
            .actorIp(log.getActorIp())
            .targetType(log.getTargetType().name())
            .targetId(log.getTargetId())
            .targetName(log.getTargetName())
            .action(log.getAction())
            .details(log.getDetails())
            .changeData(log.getChangeData())
            .success(log.getSuccess())
            .errorMessage(log.getErrorMessage())
            .requestId(log.getRequestId())
            .timestamp(log.getTimestamp())
            .build();
    }
}
