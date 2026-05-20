package com.enterprise.iam.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    
    private String id;
    
    private String eventType;
    
    private String actorId;
    
    private String actorUsername;
    
    private String actorIp;
    
    private String targetType;
    
    private String targetId;
    
    private String targetName;
    
    private String action;
    
    private String details;
    
    private String changeData;
    
    private Boolean success;
    
    private String errorMessage;
    
    private String requestId;
    
    private Instant timestamp;
}
