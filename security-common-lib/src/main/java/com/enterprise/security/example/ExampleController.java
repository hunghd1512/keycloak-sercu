package com.enterprise.security.example;

import com.enterprise.security.annotation.CurrentUser;
import com.enterprise.security.annotation.HasRole;
import com.enterprise.security.principal.CurrentUserService;
import com.enterprise.security.principal.EnterpriseUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Example controller demonstrating how to use security-common-lib.
 * This is a sample reference implementation.
 */
@RestController
@RequestMapping("/api/v1/example")
public class ExampleController {
    
    private final ExampleService exampleService;
    private final CurrentUserService currentUserService;
    
    public ExampleController(ExampleService exampleService, CurrentUserService currentUserService) {
        this.exampleService = exampleService;
        this.currentUserService = currentUserService;
    }
    
    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getProfile(@CurrentUser EnterpriseUserPrincipal user) {
        UserProfile profile = new UserProfile(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getFirstName(),
            user.getLastName()
        );
        return ResponseEntity.ok(profile);
    }
    
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getCurrentUser(@CurrentUser String userId) {
        EnterpriseUserPrincipal user = currentUserService.requireCurrentUser();
        return ResponseEntity.ok(Map.of(
            "id", userId,
            "username", user.getUsername(),
            "email", user.getEmail()
        ));
    }
    
    @PostMapping("/admin-action")
    @HasRole("ADMIN")
    public ResponseEntity<String> adminAction() {
        return ResponseEntity.ok("Admin action executed");
    }
    
    @GetMapping("/moderator-panel")
    @HasRole({"ADMIN", "MODERATOR"})
    public ResponseEntity<String> moderatorPanel() {
        return ResponseEntity.ok("Moderator panel accessed");
    }
    
    @PostMapping("/super-action")
    @HasRole("ADMIN")
    @HasRole("SUPER_USER")
    public ResponseEntity<String> superAction() {
        return ResponseEntity.ok("Super action executed");
    }
    
    @GetMapping("/check-role")
    public ResponseEntity<Map<String, Boolean>> checkRoles() {
        EnterpriseUserPrincipal user = currentUserService.requireCurrentUser();
        return ResponseEntity.ok(Map.of(
            "isAdmin", user.hasRole("ADMIN"),
            "isUser", user.hasRole("USER"),
            "isModerator", user.hasAnyRole("ADMIN", "MODERATOR")
        ));
    }
    
    @GetMapping("/check-programmatic")
    public ResponseEntity<Map<String, Object>> checkProgrammatic() {
        return ResponseEntity.ok(Map.of(
            "userId", currentUserService.getCurrentUserId(),
            "username", currentUserService.getCurrentUsername(),
            "hasAdminRole", currentUserService.hasRole("ADMIN"),
            "hasAnyManagerRole", currentUserService.hasAnyRole("MANAGER", "ADMIN")
        ));
    }
    
    @GetMapping("/client-roles")
    public ResponseEntity<Map<String, Object>> getClientRoles() {
        EnterpriseUserPrincipal user = currentUserService.requireCurrentUser();
        return ResponseEntity.ok(Map.of(
            "hasDocumentEditor", user.hasClientRole("document-service", "DOC_EDITOR"),
            "documentServiceRoles", (Object) user.getClientRolesList("document-service")
        ));
    }
    
    @GetMapping("/attributes")
    public ResponseEntity<Map<String, Object>> getAttributes() {
        EnterpriseUserPrincipal user = currentUserService.requireCurrentUser();
        return ResponseEntity.ok(Map.of(
            "departmentId", user.getStringAttribute("department_id") != null ? 
                user.getStringAttribute("department_id") : "Not set",
            "employeeId", user.getStringAttribute("employee_id") != null ?
                user.getStringAttribute("employee_id") : "Not set"
        ));
    }
    
    public record UserProfile(
        String id,
        String username,
        String email,
        String displayName,
        String firstName,
        String lastName
    ) {}
    
    public interface ExampleService {
        UserProfile getProfile(String userId);
    }
}
