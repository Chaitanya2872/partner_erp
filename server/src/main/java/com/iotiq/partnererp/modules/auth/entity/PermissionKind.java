package com.iotiq.partnererp.modules.auth.entity;

/**
 * GENERIC actions (delete, export, approve) are flags on each module permission; MODULE actions belong
 * to one module and are granted individually.
 */
public enum PermissionKind {
    GENERIC,
    MODULE
}
