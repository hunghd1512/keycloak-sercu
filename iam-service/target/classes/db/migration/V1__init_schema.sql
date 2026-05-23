-- IAM Service Database Schema
-- Version: 1.0.0

-- Organizations table (create first due to foreign key references)
CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    parent_id VARCHAR(36),
    path VARCHAR(1000),
    level INTEGER NOT NULL DEFAULT 0,
    type VARCHAR(50) NOT NULL DEFAULT 'DEPARTMENT',
    manager_id VARCHAR(36),
    location VARCHAR(100),
    cost_center VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES organizations(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_org_code ON organizations(code);
CREATE INDEX IF NOT EXISTS idx_org_parent ON organizations(parent_id);
CREATE INDEX IF NOT EXISTS idx_org_path ON organizations(path);
CREATE INDEX IF NOT EXISTS idx_org_enabled ON organizations(enabled);

-- IAM Users table
CREATE TABLE IF NOT EXISTS iam_users (
    id VARCHAR(36) PRIMARY KEY,
    keycloak_user_id VARCHAR(36) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    phone_number VARCHAR(20),
    employee_id VARCHAR(50),
    title VARCHAR(100),
    department_id VARCHAR(36),
    manager_id VARCHAR(36),
    cost_center VARCHAR(50),
    location VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    hire_date TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES organizations(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_manager FOREIGN KEY (manager_id) REFERENCES iam_users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_user_keycloak_id ON iam_users(keycloak_user_id);
CREATE INDEX IF NOT EXISTS idx_user_email ON iam_users(email);
CREATE INDEX IF NOT EXISTS idx_user_username ON iam_users(username);
CREATE INDEX IF NOT EXISTS idx_user_department ON iam_users(department_id);
CREATE INDEX IF NOT EXISTS idx_user_enabled ON iam_users(enabled);
CREATE INDEX IF NOT EXISTS idx_user_manager ON iam_users(manager_id);

-- User custom attributes
CREATE TABLE IF NOT EXISTS user_custom_attributes (
    user_id VARCHAR(36) NOT NULL,
    attribute_key VARCHAR(255) NOT NULL,
    attribute_value TEXT,
    PRIMARY KEY (user_id, attribute_key),
    CONSTRAINT fk_attr_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_attr_user ON user_custom_attributes(user_id);

-- Roles table
CREATE TABLE IF NOT EXISTS iam_roles (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    client_id VARCHAR(100),
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_composite BOOLEAN NOT NULL DEFAULT FALSE,
    role_type VARCHAR(50) NOT NULL DEFAULT 'REALM',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_role_client FOREIGN KEY (client_id) REFERENCES organizations(id) ON DELETE SET NULL,
    CONSTRAINT uq_role_name_client UNIQUE (name, client_id)
);

CREATE INDEX IF NOT EXISTS idx_role_name ON iam_roles(name);
CREATE INDEX IF NOT EXISTS idx_role_client ON iam_roles(client_id);
CREATE INDEX IF NOT EXISTS idx_role_enabled ON iam_roles(enabled);
CREATE INDEX IF NOT EXISTS idx_role_type ON iam_roles(role_type);

-- Permissions table
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(100),
    action VARCHAR(50),
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_permission_name ON permissions(name);
CREATE INDEX IF NOT EXISTS idx_permission_resource ON permissions(resource);
CREATE INDEX IF NOT EXISTS idx_permission_enabled ON permissions(enabled);

-- Role-Permission mapping
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES iam_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rp_role ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_rp_permission ON role_permissions(permission_id);

-- User-Role mapping
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES iam_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ur_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_ur_role ON user_roles(role_id);

-- Role hierarchy
CREATE TABLE IF NOT EXISTS role_hierarchy (
    parent_role_id VARCHAR(36) NOT NULL,
    child_role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (parent_role_id, child_role_id),
    CONSTRAINT fk_rh_parent FOREIGN KEY (parent_role_id) REFERENCES iam_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rh_child FOREIGN KEY (child_role_id) REFERENCES iam_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rh_parent ON role_hierarchy(parent_role_id);
CREATE INDEX IF NOT EXISTS idx_rh_child ON role_hierarchy(child_role_id);

-- Organization-User mapping
CREATE TABLE IF NOT EXISTS organization_users (
    organization_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT fk_ou_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ou_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ou_org ON organization_users(organization_id);
CREATE INDEX IF NOT EXISTS idx_ou_user ON organization_users(user_id);

-- User Role Assignments (detailed tracking)
CREATE TABLE IF NOT EXISTS user_role_assignments (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    assigned_by VARCHAR(36) NOT NULL,
    assigned_by_username VARCHAR(100),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_by VARCHAR(36),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(500),
    CONSTRAINT fk_ura_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ura_role FOREIGN KEY (role_id) REFERENCES iam_roles(id) ON DELETE CASCADE,
    CONSTRAINT uq_ura_unique UNIQUE (user_id, role_id, assigned_by)
);

CREATE INDEX IF NOT EXISTS idx_ura_user ON user_role_assignments(user_id);
CREATE INDEX IF NOT EXISTS idx_ura_role ON user_role_assignments(role_id);
CREATE INDEX IF NOT EXISTS idx_ura_assigned_by ON user_role_assignments(assigned_by);
CREATE INDEX IF NOT EXISTS idx_ura_active ON user_role_assignments(is_active);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    actor_ip VARCHAR(45),
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(36),
    target_name VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    details TEXT,
    change_data JSONB,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    request_id VARCHAR(100),
    user_agent VARCHAR(500),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_target ON audit_logs(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_event ON audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_success ON audit_logs(success);

-- Insert default roles
INSERT INTO iam_roles (id, name, description, enabled, is_composite, role_type, created_at) VALUES
    (gen_random_uuid()::text, 'SUPER_ADMIN', 'Super Administrator - full system access', true, true, 'REALM', NOW()),
    (gen_random_uuid()::text, 'ADMIN', 'Administrator - system configuration', true, true, 'REALM', NOW()),
    (gen_random_uuid()::text, 'USER_MANAGER', 'User Manager - manage users and roles', true, false, 'REALM', NOW()),
    (gen_random_uuid()::text, 'AUDITOR', 'Auditor - read-only access', true, false, 'REALM', NOW()),
    (gen_random_uuid()::text, 'SUPPORT', 'Support - limited support access', true, false, 'REALM', NOW()),
    (gen_random_uuid()::text, 'USER', 'Base user - standard access', true, false, 'REALM', NOW())
ON CONFLICT (name) DO NOTHING;

-- Insert default permissions
INSERT INTO permissions (id, name, resource, action, description, enabled, created_at) VALUES
    (gen_random_uuid()::text, 'user:create', 'user', 'create', 'Create new users', true, NOW()),
    (gen_random_uuid()::text, 'user:read', 'user', 'read', 'Read user information', true, NOW()),
    (gen_random_uuid()::text, 'user:update', 'user', 'update', 'Update user information', true, NOW()),
    (gen_random_uuid()::text, 'user:delete', 'user', 'delete', 'Delete users', true, NOW()),
    (gen_random_uuid()::text, 'role:create', 'role', 'create', 'Create new roles', true, NOW()),
    (gen_random_uuid()::text, 'role:read', 'role', 'read', 'Read role information', true, NOW()),
    (gen_random_uuid()::text, 'role:assign', 'role', 'assign', 'Assign roles to users', true, NOW()),
    (gen_random_uuid()::text, 'org:create', 'org', 'create', 'Create organizations', true, NOW()),
    (gen_random_uuid()::text, 'org:read', 'org', 'read', 'Read organization information', true, NOW()),
    (gen_random_uuid()::text, 'org:update', 'org', 'update', 'Update organizations', true, NOW()),
    (gen_random_uuid()::text, 'audit:read', 'audit', 'read', 'Read audit logs', true, NOW())
ON CONFLICT (name) DO NOTHING;

-- Create default organization
INSERT INTO organizations (id, code, name, description, path, level, type, enabled, created_at) VALUES
    (gen_random_uuid()::text, 'ROOT', 'Enterprise', 'Root organization', '/' || gen_random_uuid()::text, 0, 'COMPANY', true, NOW())
ON CONFLICT (code) DO NOTHING;
