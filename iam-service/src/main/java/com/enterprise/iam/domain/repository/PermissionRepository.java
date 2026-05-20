package com.enterprise.iam.domain.repository;

import com.enterprise.iam.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    
    Optional<Permission> findByName(String name);
    
    @Query("SELECT p FROM Permission p LEFT JOIN FETCH p.roles WHERE p.id = :id")
    Optional<Permission> findByIdWithRoles(@Param("id") String id);
    
    @Query("SELECT DISTINCT p FROM Permission p JOIN p.roles r WHERE r.id = :roleId AND p.enabled = true")
    Set<Permission> findByRoleId(@Param("roleId") String roleId);
    
    @Query("SELECT p FROM Permission p WHERE p.resource = :resource AND p.enabled = true")
    List<Permission> findByResource(@Param("resource") String resource);
    
    @Query("SELECT p FROM Permission p WHERE p.resource = :resource AND p.action = :action AND p.enabled = true")
    Optional<Permission> findByResourceAndAction(@Param("resource") String resource, @Param("action") String action);
    
    boolean existsByName(String name);
    
    @Query("SELECT p FROM Permission p WHERE p.enabled = true")
    List<Permission> findAllEnabled();
}
