package com.enterprise.iam.domain.repository;

import com.enterprise.iam.domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {
    
    Optional<Organization> findByCode(String code);
    
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.children WHERE o.parent IS NULL AND o.enabled = true")
    List<Organization> findRootOrganizations();
    
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.children WHERE o.id = :id AND o.enabled = true")
    Optional<Organization> findByIdWithChildren(@Param("id") String id);
    
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.users WHERE o.id = :id")
    Optional<Organization> findByIdWithUsers(@Param("id") String id);
    
    @Query("SELECT o FROM Organization o WHERE o.parent.id = :parentId AND o.enabled = true")
    List<Organization> findByParentId(@Param("parentId") String parentId);
    
    @Query("SELECT o FROM Organization o WHERE o.path LIKE CONCAT(:path, '/%') AND o.enabled = true")
    List<Organization> findDescendants(@Param("path") String path);
    
    @Query("SELECT o FROM Organization o WHERE o.managerId = :managerId AND o.enabled = true")
    List<Organization> findByManagerId(@Param("managerId") String managerId);
    
    @Query("SELECT o FROM Organization o JOIN o.users u WHERE u.id = :userId AND o.enabled = true")
    Set<Organization> findByUserId(@Param("userId") String userId);
    
    boolean existsByCode(String code);
    
    @Query("SELECT COUNT(o) FROM Organization o WHERE o.enabled = true")
    long countEnabledOrganizations();
}
