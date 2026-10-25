-- Phase 5: module action that reveals partner PAN and bank account numbers (otherwise masked, BR-P9).
-- Granted to nobody by default: Super Admins hold every catalogue action (computed), other accounts only
-- when a Super Admin grants it explicitly. No role template is changed.

INSERT INTO permissions (code, display_name, description, kind, module_id, super_admin_only, sort_order)
VALUES ('VIEW_PARTNER_SENSITIVE', 'View partner PAN and bank details',
        'See full PAN and bank account numbers (otherwise masked to the last 4 characters)', 'MODULE',
        (SELECT id FROM modules WHERE code = 'PARTNERS'), false, 10);
