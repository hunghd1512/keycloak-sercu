package com.enterprise.iam.domain.repository;

import com.enterprise.iam.domain.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, String> {
    
    List<UserRoleAssignment> findByUserIdAndIsActiveTrue(String userId);
    
    List<UserRoleAssignment> findByRoleIdAndIsActiveTrue(String roleId);
    
    Optional<UserRoleAssignment> findByUserIdAndRoleIdAndIsActiveTrue(String userId, String roleId);
    
    @Query("SELECT ura FROM UserRoleAssignment ura WHERE ura.user.id = :userId AND ura.role.id = :roleId")
    Optional<UserRoleAssignment> findByUserIdAndRoleId(@Param("userId") String userId, @Param("roleId") String roleId);
    
    @Query("SELECT ura FROM UserRoleAssignment ura WHERE ura.user.keycloakUserId = :keycloakUserId AND ura.role.name = :roleName")
    Optional<UserRoleAssignment> findByKeycloakUserIdAndRoleName(@Param("keycloakUserId") String keycloakUserId, @Param("roleName") String roleName);
    
    @Modifying
    @Query("UPDATE UserRoleAssignment ura SET ura.isActive = false, ura.revokedAt = :revokedAt, ura.revokedBy = :revokedBy WHERE ura.user.id = :userId AND ura.role.id = :roleId AND ura.isActive = true")
    int revokeRoleAssignment(@Param("userId") String userId, 
                           @Param("roleId") String roleId, 
                           @Param("revokedBy") String revokedBy, 
                           @Param("revokedAt") Instant revokedAt);
    
    @Modifying
    @Query("UPDATE UserRoleAssignment ura SET ura.isActive = false, ura.revokedAt = :revokedAt, ura.revokedBy = :revokedBy WHERE ura.user.id = :userId AND ura.isActive = true")
    int revokeAllUserRoles(@Param("userId") String userId, 
                          @Param("revokedBy") String revokedBy, 
                          @Param("revokedAt") Instant revokedAt);
    
    @Query("SELECT COUNT(ura) FROM UserRoleAssignment ura WHERE ura.role.id = :roleId AND ura.isActive = true")
    long countActiveByRoleId(@Param("roleId") String roleId);
    
    @Query("SELECT COUNT(ura) FROM UserRoleAssignment ura WHERE ura.user.id = :userId AND ura.isActive = true")
    long countActiveByUserId(@Param("userId") String userId);
    
    @Query("SELECT ura FROM UserRoleAssignment ura WHERE ura.expiresAt IS NOT NULL AND ura.expiresAt < :now AND ura.isActive = true")
    List<UserRoleAssignment> findExpiredAssignments(@Param("now") Instant now);
    
    @Modifying
    @Query("UPDATE UserRoleAssignment ura SET ura.isActive = false WHERE ura.expiresAt IS NOT NULL AND ura.expiresAt < :now AND ura.isActive = true")
    int expireAssignments(@Param("now") Instant now);
}
