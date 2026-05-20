package com.enterprise.iam.domain.repository;

import com.enterprise.iam.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    
    Optional<Role> findByName(String name);
    
    Optional<Role> findByNameAndClientId(String name, String clientId);
    
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(@Param("id") String id);
    
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name AND r.clientId = :clientId")
    Optional<Role> findByNameAndClientIdWithPermissions(@Param("name") String name, @Param("clientId") String clientId);
    
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.childRoles WHERE r.id = :id")
    Optional<Role> findByIdWithChildRoles(@Param("id") String id);
    
    @Query("SELECT r FROM Role r WHERE r.enabled = true")
    List<Role> findAllEnabled();
    
    @Query("SELECT r FROM Role r WHERE r.clientId IS NULL AND r.enabled = true")
    List<Role> findAllRealmRoles();
    
    @Query("SELECT r FROM Role r WHERE r.clientId = :clientId AND r.enabled = true")
    List<Role> findAllClientRoles(@Param("clientId") String clientId);
    
    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN r.users u WHERE u.id = :userId AND r.enabled = true")
    Set<Role> findByUserId(@Param("userId") String userId);
    
    boolean existsByName(String name);
    
    boolean existsByNameAndClientId(String name, String clientId);
    
    @Query("SELECT COUNT(r) FROM Role r WHERE r.enabled = true")
    long countEnabledRoles();
}
