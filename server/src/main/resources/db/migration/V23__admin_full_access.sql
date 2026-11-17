-- Owner decision: the ADMIN role has the same access as SUPER_ADMIN (all modules, all actions), including the
-- items that were reserved for Super Admin. Super Admin ACCOUNTS stay protected in the application layer.

UPDATE modules SET super_admin_only = false WHERE super_admin_only;
UPDATE permissions SET super_admin_only = false WHERE super_admin_only;
UPDATE permissions SET description = 'Change user module and action permissions'
 WHERE code = 'MANAGE_PERMISSIONS';

-- ADMIN role template: every module, every flag (the application caps flags by the module's supports_* columns).
INSERT INTO role_permission_templates (role_id, module_id, can_write, can_delete, can_export, can_approve, data_scope)
SELECT r.id, m.id, true, true, true, true, 'ALL'
  FROM roles r CROSS JOIN modules m
 WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, module_id) DO UPDATE
   SET can_write = true, can_delete = true, can_export = true, can_approve = true, data_scope = 'ALL';

-- Existing ADMIN accounts get the same access.
INSERT INTO user_module_permissions (user_id, module_id, can_read, can_write, can_delete, can_export, can_approve, data_scope)
SELECT u.id, m.id, true, true, true, true, true, 'ALL'
  FROM users u
  JOIN roles r ON r.id = u.role_id AND r.code = 'ADMIN'
 CROSS JOIN modules m
ON CONFLICT (user_id, module_id) DO UPDATE
   SET can_read = true, can_write = true, can_delete = true, can_export = true, can_approve = true,
       data_scope = 'ALL';

INSERT INTO user_action_permissions (user_id, permission_id)
SELECT u.id, p.id
  FROM users u
  JOIN roles r ON r.id = u.role_id AND r.code = 'ADMIN'
 CROSS JOIN permissions p
 WHERE p.active AND p.kind = 'MODULE'
ON CONFLICT DO NOTHING;
