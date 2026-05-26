package com.enterprise.iam.service;

import com.enterprise.iam.application.dto.OrganizationDto;
import com.enterprise.iam.application.dto.CreateOrganizationRequest;
import com.enterprise.iam.domain.entity.Organization;
import com.enterprise.iam.domain.repository.OrganizationRepository;
import com.enterprise.iam.exception.OrganizationNotFoundException;
import com.enterprise.iam.exception.OrganizationCodeExistsException;
import com.enterprise.iam.exception.InvalidOrganizationHierarchyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {
    
    private final OrganizationRepository organizationRepository;
    
    @Transactional
    @CacheEvict(value = {"organizations", "orgTree"}, allEntries = true)
    public OrganizationDto createOrganization(CreateOrganizationRequest request, 
                                            String actorId, String actorUsername) {
        log.info("Creating organization: {} by {}", request.getCode(), actorUsername);
        
        // Validate code uniqueness
        if (organizationRepository.existsByCode(request.getCode())) {
            throw new OrganizationCodeExistsException("Organization code already exists: " + request.getCode());
        }
        
        // Build organization
        Organization org = Organization.builder()
            .code(request.getCode())
            .name(request.getName())
            .description(request.getDescription())
            .type(Organization.OrganizationType.valueOf(request.getType().name()))
            .managerId(request.getManagerId())
            .location(request.getLocation())
            .costCenter(request.getCostCenter())
            .enabled(true)
            .users(new HashSet<>())
            .children(new HashSet<>())
            .build();
        
        // Set parent if provided
        if (request.getParentId() != null) {
            Organization parent = organizationRepository.findById(request.getParentId())
                .orElseThrow(() -> new OrganizationNotFoundException("Parent organization not found: " + request.getParentId()));
            
            org.setParent(parent);
            org.updatePath();
            org.setLevel(parent.getLevel() + 1);
        } else {
            org.updatePath();
        }
        
        org = organizationRepository.save(org);

        log.info("Organization created successfully: {}", org.getId());
        
        return toOrganizationDto(org);
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "organizations", key = "#orgId")
    public OrganizationDto getOrganizationById(String orgId) {
        Organization org = organizationRepository.findByIdWithChildren(orgId)
            .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + orgId));
        
        return toOrganizationDto(org);
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "organizations", key = "'code:' + #code")
    public OrganizationDto getOrganizationByCode(String code) {
        Organization org = organizationRepository.findByCode(code)
            .orElseThrow(() -> new OrganizationNotFoundException("Organization not found with code: " + code));
        
        return toOrganizationDto(org);
    }
    
    @Transactional(readOnly = true)
    public List<OrganizationDto> getRootOrganizations() {
        return organizationRepository.findRootOrganizations().stream()
            .map(this::toOrganizationDto)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "orgTree", key = "'tree:' + #orgId")
    public OrganizationDto getOrganizationTree(String orgId) {
        Organization org = organizationRepository.findByIdWithChildren(orgId)
            .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + orgId));
        
        return buildOrganizationTree(org);
    }
    
    @Transactional(readOnly = true)
    public List<OrganizationDto> getOrganizationDescendants(String orgId) {
        Organization org = organizationRepository.findById(orgId)
            .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + orgId));
        
        return organizationRepository.findDescendants(org.getPath()).stream()
            .map(this::toOrganizationDto)
            .collect(Collectors.toList());
    }
    
    @Transactional
    @CacheEvict(value = {"organizations", "orgTree"}, allEntries = true)
    public OrganizationDto updateOrganization(String orgId, CreateOrganizationRequest request,
                                            String actorId, String actorUsername) {
        log.info("Updating organization: {} by {}", orgId, actorUsername);
        
        Organization org = organizationRepository.findById(orgId)
            .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + orgId));
        
        // Update fields
        if (request.getName() != null) {
            org.setName(request.getName());
        }
        
        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }
        
        if (request.getType() != null) {
            org.setType(Organization.OrganizationType.valueOf(request.getType().name()));
        }
        
        if (request.getManagerId() != null) {
            org.setManagerId(request.getManagerId());
        }
        
        if (request.getLocation() != null) {
            org.setLocation(request.getLocation());
        }
        
        if (request.getCostCenter() != null) {
            org.setCostCenter(request.getCostCenter());
        }
        
        // Update parent if changed
        if (request.getParentId() != null) {
            if (request.getParentId().equals(org.getParent() != null ? org.getParent().getId() : null)) {
                // Parent unchanged
            } else {
                // Validate new parent
                if (request.getParentId().equals(orgId)) {
                    throw new InvalidOrganizationHierarchyException("Organization cannot be its own parent");
                }
                
                Organization newParent = organizationRepository.findById(request.getParentId())
                    .orElseThrow(() -> new OrganizationNotFoundException("Parent organization not found"));
                
                // Check for circular reference
                if (isDescendant(newParent, orgId)) {
                    throw new InvalidOrganizationHierarchyException("Cannot set descendant as parent");
                }
                
                org.setParent(newParent);
                org.updatePath();
                org.setLevel(newParent.getLevel() + 1);
                
                // Update all descendants' paths
                updateDescendantPaths(org);
            }
        }
        
        org = organizationRepository.save(org);

        log.info("Organization updated successfully: {}", orgId);
        
        return toOrganizationDto(org);
    }
    
    @Transactional
    @CacheEvict(value = {"organizations", "orgTree"}, allEntries = true)
    public void deleteOrganization(String orgId, String actorId, String actorUsername) {
        log.info("Deleting organization: {} by {}", orgId, actorUsername);
        
        Organization org = organizationRepository.findById(orgId)
            .orElseThrow(() -> new OrganizationNotFoundException("Organization not found: " + orgId));
        
        // Check if has children
        if (!org.getChildren().isEmpty()) {
            throw new InvalidOrganizationHierarchyException("Cannot delete organization with children");
        }
        
        // Check if has users
        if (!org.getUsers().isEmpty()) {
            throw new InvalidOrganizationHierarchyException("Cannot delete organization with users");
        }
        
        org.setEnabled(false);
        organizationRepository.save(org);
        
        log.info("Organization deleted successfully: {}", orgId);
    }
    
    private OrganizationDto toOrganizationDto(Organization org) {
        return OrganizationDto.builder()
            .id(org.getId())
            .code(org.getCode())
            .name(org.getName())
            .description(org.getDescription())
            .parentId(org.getParent() != null ? org.getParent().getId() : null)
            .parentName(org.getParent() != null ? org.getParent().getName() : null)
            .path(org.getPath())
            .level(org.getLevel())
            .type(OrganizationDto.OrganizationType.valueOf(org.getType().name()))
            .managerId(org.getManagerId())
            .location(org.getLocation())
            .costCenter(org.getCostCenter())
            .enabled(org.getEnabled())
            .userCount(org.getUsers() != null ? org.getUsers().size() : 0)
            .createdAt(org.getCreatedAt())
            .updatedAt(org.getUpdatedAt())
            .build();
    }
    
    private OrganizationDto buildOrganizationTree(Organization org) {
        OrganizationDto dto = toOrganizationDto(org);
        
        if (org.getChildren() != null && !org.getChildren().isEmpty()) {
            List<OrganizationDto> children = org.getChildren().stream()
                .filter(Organization::getEnabled)
                .map(this::buildOrganizationTree)
                .collect(Collectors.toList());
            dto.setChildren(children);
        }
        
        return dto;
    }
    
    private boolean isDescendant(Organization org, String childId) {
        if (org.getChildren() == null) {
            return false;
        }
        
        for (Organization child : org.getChildren()) {
            if (child.getId().equals(childId)) {
                return true;
            }
            if (isDescendant(child, childId)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void updateDescendantPaths(Organization org) {
        if (org.getChildren() == null) {
            return;
        }
        
        for (Organization child : org.getChildren()) {
            child.updatePath();
            child.setLevel(org.getLevel() + 1);
            organizationRepository.save(child);
            updateDescendantPaths(child);
        }
    }
}
