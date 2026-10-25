package com.iotiq.partnererp.security;

import com.iotiq.partnererp.modules.auth.entity.DataScope;

/**
 * Effective access to one module. Present only when the user can at least read the module.
 */
public record ModuleAccess(boolean write, boolean delete, boolean export, boolean approve, DataScope scope) {
}
