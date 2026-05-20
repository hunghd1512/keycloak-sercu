package com.enterprise.iam.domain.repository;

import com.enterprise.iam.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    
    @Query("SELECT a FROM AuditLog a WHERE a.actorId = :actorId ORDER BY a.timestamp DESC")
    Page<AuditLog> findByActorId(@Param("actorId") String actorId, Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.targetType = :targetType AND a.targetId = :targetId ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTarget(@Param("targetType") AuditLog.TargetType targetType, 
                                 @Param("targetId") String targetId, 
                                 Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.eventType = :eventType ORDER BY a.timestamp DESC")
    Page<AuditLog> findByEventType(@Param("eventType") AuditLog.AuditEventType eventType, Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(@Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate,
                                          Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.actorId = :actorId AND a.eventType = :eventType ORDER BY a.timestamp DESC")
    Page<AuditLog> findByActorIdAndEventType(@Param("actorId") String actorId,
                                              @Param("eventType") AuditLog.AuditEventType eventType,
                                              Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.success = false ORDER BY a.timestamp DESC")
    Page<AuditLog> findFailedEvents(Pageable pageable);
    
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.eventType = :eventType AND a.timestamp >= :since")
    long countEventsSince(@Param("eventType") AuditLog.AuditEventType eventType,
                         @Param("since") Instant since);
    
    @Query("SELECT a FROM AuditLog a WHERE a.targetType = :targetType ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentByTargetType(@Param("targetType") AuditLog.TargetType targetType, Pageable pageable);
}
