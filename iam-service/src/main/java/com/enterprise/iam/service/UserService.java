package com.enterprise.iam.service;

import com.enterprise.iam.application.dto.*;
import com.enterprise.iam.config.IamProperties;
import com.enterprise.iam.domain.entity.Role;
import com.enterprise.iam.domain.entity.User;
import com.enterprise.iam.domain.repository.RoleRepository;
import com.enterprise.iam.domain.repository.UserRepository;
import com.enterprise.iam.exception.*;
import com.enterprise.iam.integration.keycloak.KeycloakClient;
import com.enterprise.iam.integration.keycloak.KeycloakUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.enterprise.iam.domain.entity.UserRoleAssignment;
import com.enterprise.iam.domain.repository.UserRoleAssignmentRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final KeycloakClient keycloakClient;
    private final IamProperties iamProperties;

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public UserDto createUser(CreateUserRequest request, String actorId, String actorUsername) {
        log.info("Creating user: {} by {}", request.getUsername(), actorUsername);

        // Validate email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        // Validate username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists: " + request.getUsername());
        }

        // Create user in Keycloak
        UserDto userDto = UserDto.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .displayName(request.getDisplayName())
            .phoneNumber(request.getPhoneNumber())
            .employeeId(request.getEmployeeId())
            .title(request.getTitle())
            .departmentId(request.getDepartmentId())
            .enabled(true)
            .emailVerified(false)
            .build();

        Map<String, Object> keycloakUser = KeycloakUserMapper.toKeycloakRepresentation(userDto);

        // Set temporary password for new user
        keycloakUser.put("credentials", List.of(Map.of(
            "type", "password",
            "value", generateTempPassword(),
            "temporary", request.getMustChangePassword() != null && request.getMustChangePassword()
        )));

        JsonNode keycloakResponse = keycloakClient.createUser(keycloakUser);
        String keycloakUserId = keycloakResponse.has("id") ? keycloakResponse.get("id").asText() : null;

        if (keycloakUserId == null) {
            throw new RuntimeException("Failed to create user in Keycloak");
        }

        // If email verification required
        if (iamProperties.getPolicy().isRequireEmailVerification()) {
            keycloakClient.sendVerificationEmail(keycloakUserId);
        }

        // Create user in IAM database
        User user = User.builder()
            .keycloakUserId(keycloakUserId)
            .username(request.getUsername())
            .email(request.getEmail())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .displayName(request.getDisplayName())
            .phoneNumber(request.getPhoneNumber())
            .employeeId(request.getEmployeeId())
            .title(request.getTitle())
            .departmentId(request.getDepartmentId())
            .managerId(request.getManagerId())
            .costCenter(request.getCostCenter())
            .location(request.getLocation())
            .hireDate(request.getHireDate())
            .description(request.getDescription())
            .enabled(true)
            .emailVerified(false)
            .createdBy(actorId)
            .build();

        if (request.getCustomAttributes() != null) {
            user.setCustomAttributes(request.getCustomAttributes());
        }

        user = userRepository.save(user);

        // Assign default roles
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            assignRolesToUser(user, request.getRoles(), actorId, actorUsername);
        } else {
            // Assign default role
            String defaultRole = iamProperties.getPolicy().getDefaultUserRole();
            assignRolesToUser(user, List.of(defaultRole), actorId, actorUsername);
        }

        log.info("User created successfully: {}", user.getId());

        return toUserDto(user);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public UserDto getUserById(String userId) {
        User user = userRepository.findByIdWithRoles(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return toUserDto(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "'keycloak:' + #keycloakUserId")
    public UserDto getUserByKeycloakId(String keycloakUserId) {
        User user = userRepository.findByKeycloakUserIdWithRoles(keycloakUserId)
            .orElseThrow(() -> new UserNotFoundException("User not found with Keycloak ID: " + keycloakUserId));

        return toUserDto(user);
    }

    @Transactional(readOnly = true)
    public UserDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        return toUserDto(user);
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(this::toUserDto);
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getEnabledUsers(Pageable pageable) {
        return userRepository.findAllEnabled(pageable)
            .map(this::toUserDto);
    }

    @Transactional(readOnly = true)
    public Page<UserDto> searchUsers(String search, Pageable pageable) {
        return userRepository.searchUsers(search, pageable)
            .map(this::toUserDto);
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getUsersByDepartment(String departmentId, Pageable pageable) {
        return userRepository.findByDepartmentId(departmentId, pageable)
            .map(this::toUserDto);
    }

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public UserDto updateUser(String userId, UpdateUserRequest request,
                              String actorId, String actorUsername) {
        log.info("Updating user: {} by {}", userId, actorUsername);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Update basic info
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());

            // Update in Keycloak
            Map<String, Object> keycloakUpdate = new HashMap<>();
            keycloakUpdate.put("email", request.getEmail());
            keycloakClient.updateUser(user.getKeycloakUserId(), keycloakUpdate);
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getTitle() != null) {
            user.setTitle(request.getTitle());
        }

        if (request.getDepartmentId() != null) {
            user.setDepartmentId(request.getDepartmentId());
        }

        if (request.getCustomAttributes() != null) {
            user.setCustomAttributes(request.getCustomAttributes());
        }

        user.setUpdatedBy(actorId);
        user = userRepository.save(user);

        log.info("User updated successfully: {}", userId);

        return toUserDto(user);
    }

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public void disableUser(String userId, String actorId, String actorUsername) {
        log.info("Disabling user: {} by {}", userId, actorUsername);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        user.setEnabled(false);
        user.setUpdatedBy(actorId);
        userRepository.save(user);

        // Disable in Keycloak
        keycloakClient.enableUser(user.getKeycloakUserId(), false);

        log.info("User disabled successfully: {}", userId);
    }

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public void enableUser(String userId, String actorId, String actorUsername) {
        log.info("Enabling user: {} by {}", userId, actorUsername);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        user.setEnabled(true);
        user.setUpdatedBy(actorId);
        userRepository.save(user);

        // Enable in Keycloak
        keycloakClient.enableUser(user.getKeycloakUserId(), true);

        log.info("User enabled successfully: {}", userId);
    }

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public void deleteUser(String userId, String actorId, String actorUsername) {
        log.info("Deleting user: {} by {}", userId, actorUsername);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        String keycloakUserId = user.getKeycloakUserId();

        // Soft delete - just disable in Keycloak
        keycloakClient.enableUser(keycloakUserId, false);

        user.setEnabled(false);
        user.setUpdatedBy(actorId);
        userRepository.save(user);

        log.info("User deleted successfully: {}", userId);
    }

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public void assignRolesToUser(User user, List<String> roleNames,
                                  String actorId, String actorUsername) {
        log.info("Assigning roles to user: {} by {}", user.getUsername(), actorUsername);

        String keycloakUserId = user.getKeycloakUserId();

        for (String roleName : roleNames) {
            // Get Role entity from database
            Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleName));

            // Check if assignment already exists and is active
            Optional<UserRoleAssignment> existingAssignment =
                userRoleAssignmentRepository.findByUserIdAndRoleIdAndIsActiveTrue(user.getId(), role.getId());

            if (existingAssignment.isEmpty()) {
                // Create new assignment in IAM database
                UserRoleAssignment assignment = UserRoleAssignment.builder()
                    .user(user)
                    .role(role)
                    .assignedBy(actorId)
                    .assignedByUsername(actorUsername)
                    .assignedAt(Instant.now())
                    .isActive(true)
                    .build();

                userRoleAssignmentRepository.save(assignment);
            }

            // Assign in Keycloak (realm roles)
            JsonNode rolesNode = createRoleRepresentation(roleName, null);
            keycloakClient.assignRealmRoles(keycloakUserId, rolesNode);
        }

        log.info("Roles assigned successfully to user: {}", user.getUsername());
    }

    @Transactional
    @CacheEvict(value = {"users", "userRoles"}, allEntries = true)
    public void revokeRoleFromUser(String userId, String roleName,
                                   String actorId, String actorUsername) {
        log.info("Revoking role {} from user: {} by {}", roleName, userId, actorUsername);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Get Role entity from database
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleName));

        // Revoke in IAM database - soft delete
        int updated = userRoleAssignmentRepository.revokeRoleAssignment(
            userId, role.getId(), actorId, Instant.now());

        if (updated == 0) {
            log.warn("No active role assignment found to revoke: userId={}, roleId={}", userId, role.getId());
        }

        String keycloakUserId = user.getKeycloakUserId();

        // Revoke in Keycloak
        JsonNode rolesNode = createRoleRepresentation(roleName, null);
        keycloakClient.removeRealmRoles(keycloakUserId, rolesNode);

        log.info("Role revoked successfully from user: {}", userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userRoles", key = "#userId")
    public List<RoleDto> getUserRoles(String userId) {
        User user = userRepository.findByIdWithRoles(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return user.getRoles().stream()
            .map(this::toRoleDto)
            .collect(Collectors.toList());
    }

    public void requestPasswordReset(String userId) {
        log.info("Requesting password reset for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Send password reset email via Keycloak
        keycloakClient.executeActionsEmail(user.getKeycloakUserId(),
            new String[]{"UPDATE_PASSWORD"});

        log.info("Password reset email sent to user: {}", userId);
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
            .id(user.getId())
            .keycloakUserId(user.getKeycloakUserId())
            .username(user.getUsername())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .displayName(user.getDisplayName())
            .avatarUrl(user.getAvatarUrl())
            .phoneNumber(user.getPhoneNumber())
            .employeeId(user.getEmployeeId())
            .title(user.getTitle())
            .departmentId(user.getDepartmentId())
            .managerId(user.getManagerId())
            .costCenter(user.getCostCenter())
            .location(user.getLocation())
            .enabled(user.getEnabled())
            .emailVerified(user.getEmailVerified())
            .hireDate(user.getHireDate())
            .lastLoginAt(user.getLastLoginAt())
            .description(user.getDescription())
            .customAttributes(user.getCustomAttributes())
            .roles(user.getRoles() != null ?
                user.getRoles().stream().map(this::toRoleDto).collect(Collectors.toList()) :
                Collections.emptyList())
            .createdAt(user.getCreatedAt())
            .createdBy(user.getCreatedBy())
            .updatedAt(user.getUpdatedAt())
            .updatedBy(user.getUpdatedBy())
            .build();
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
            .createdAt(role.getCreatedAt())
            .updatedAt(role.getUpdatedAt())
            .build();
    }

    private JsonNode createRoleRepresentation(String roleName, String clientId) {
        try {
            String json = String.format("[{\"name\":\"%s\"}]", roleName);
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create role representation", e);
        }
    }

    private String generateTempPassword() {
        return UUID.randomUUID().toString().substring(0, 12) + "!Aa1";
    }
}
