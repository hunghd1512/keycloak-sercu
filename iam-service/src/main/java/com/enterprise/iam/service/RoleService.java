package com.enterprise.iam.service;

import com.enterprise.iam.application.dto.RoleDto;
import com.enterprise.iam.domain.entity.Role;
import com.enterprise.iam.domain.repository.RoleRepository;
import com.enterprise.iam.exception.RoleNotFoundException;
import com.enterprise.iam.exception.RoleAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {
    
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "roles")
    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll().stream()
            .map(this::toRoleDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "roles", key = "'realm'")
    public List<RoleDto> getRealmRoles() {
        return roleRepository.findAllRealmRoles().stream()
            .map(this::toRoleDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "roles", key = "'client:' + #clientId")
    public List<RoleDto> getClientRoles(String clientId) {
        return roleRepository.findAllClientRoles(clientId).stream()
            .map(this::toRoleDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "roles", key = "#roleId")
    public RoleDto getRoleById(String roleId) {
        Role role = roleRepository.findByIdWithPermissions(roleId)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        
        return toRoleDto(role);
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "roles", key = "'name:' + #name")
    public RoleDto getRoleByName(String name) {
        Role role = roleRepository.findByName(name)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + name));
        
        return toRoleDto(role);
    }
    
    @Transactional(readOnly = true)
    public Set<RoleDto> getUserRoles(String userId) {
        return roleRepository.findByUserId(userId).stream()
            .map(this::toRoleDto)
            .collect(Collectors.toSet());
    }
    
    @Transactional
    @CacheEvict(value = "roles", allEntries = true)
    public RoleDto createRole(RoleDto roleDto, String actorId, String actorUsername) {
        log.info("Creating role: {} by {}", roleDto.getName(), actorUsername);
        
        // Validate
        if (roleRepository.existsByName(roleDto.getName())) {
            throw new RoleAlreadyExistsException("Role already exists: " + roleDto.getName());
        }
        
        Role role = Role.builder()
            .name(roleDto.getName())
            .clientId(roleDto.getClientId())
            .description(roleDto.getDescription())
            .enabled(roleDto.getEnabled() != null ? roleDto.getEnabled() : true)
            .isComposite(roleDto.getIsComposite() != null ? roleDto.getIsComposite() : false)
            .roleType(roleDto.getClientId() != null ? 
                Role.RoleType.CLIENT : Role.RoleType.REALM)
            .build();
        
        role = roleRepository.save(role);
        
        log.info("Role created successfully: {}", role.getId());
        
        return toRoleDto(role);
    }
    
    @Transactional
    @CacheEvict(value = "roles", allEntries = true)
    public RoleDto updateRole(String roleId, RoleDto roleDto, 
                             String actorId, String actorUsername) {
        log.info("Updating role: {} by {}", roleId, actorUsername);
        
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        
        if (roleDto.getDescription() != null) {
            role.setDescription(roleDto.getDescription());
        }
        
        if (roleDto.getEnabled() != null) {
            role.setEnabled(roleDto.getEnabled());
        }
        
        role = roleRepository.save(role);
        
        log.info("Role updated successfully: {}", roleId);
        
        return toRoleDto(role);
    }
    
    @Transactional
    @CacheEvict(value = "roles", allEntries = true)
    public void deleteRole(String roleId, String actorId, String actorUsername) {
        log.info("Deleting role: {} by {}", roleId, actorUsername);
        
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        
        // Check if role has users
        if (!role.getUsers().isEmpty()) {
            throw new IllegalStateException("Cannot delete role with assigned users");
        }
        
        role.setEnabled(false);
        roleRepository.save(role);
        
        log.info("Role deleted successfully: {}", roleId);
    }
    
    @Transactional
    @CacheEvict(value = "roles", allEntries = true)
    public RoleDto addChildRole(String parentRoleId, String childRoleId,
                               String actorId, String actorUsername) {
        log.info("Adding child role {} to {} by {}", childRoleId, parentRoleId, actorUsername);
        
        Role parentRole = roleRepository.findByIdWithChildRoles(parentRoleId)
            .orElseThrow(() -> new RoleNotFoundException("Parent role not found: " + parentRoleId));
        
        Role childRole = roleRepository.findById(childRoleId)
            .orElseThrow(() -> new RoleNotFoundException("Child role not found: " + childRoleId));
        
        parentRole.addChildRole(childRole);
        parentRole.setIsComposite(true);
        
        parentRole = roleRepository.save(parentRole);
        
        log.info("Child role added successfully");
        
        return toRoleDto(parentRole);
    }
    
    private RoleDto toRoleDto(Role role) {
        return RoleDto.builder()
            .id(role.getId())
            .name(role.getName())
            .clientId(role.getClientId())
            .description(role.getDescription())
            .enabled(role.getEnabled())
            .isComposite(role.getIsComposite())
            .roleType(role.getRoleType() != null ? 
                RoleDto.RoleType.valueOf(role.getRoleType().name()) : null)
            .userCount(role.getUsers() != null ? role.getUsers().size() : 0)
            .createdAt(role.getCreatedAt())
            .updatedAt(role.getUpdatedAt())
            .build();
    }
}
