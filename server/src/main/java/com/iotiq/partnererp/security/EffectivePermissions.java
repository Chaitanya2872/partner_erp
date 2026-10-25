package com.iotiq.partnererp.security;

import com.iotiq.partnererp.modules.auth.entity.DataScope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a user may do, resolved from the database on every request (so changes apply without a restart
 * or a new login). Authorities have the form {@code <MODULE>:<LEVEL>}, for example {@code LEADS:READ},
 * {@code LEADS:WRITE}, {@code INVOICES:DELETE} or {@code PAYOUTS:MARK_PAYOUT_PAID}; endpoints check them
 * with {@code @PreAuthorize("hasAuthority('LEADS:READ')")}.
 *
 * @param superAdmin  true for the SUPER_ADMIN role, whose access does not depend on stored rows
 * @param modules     module code -> access, for every module the user can read
 * @param actionCodes module-specific actions as {@code <MODULE>:<ACTION>}
 */
public record EffectivePermissions(boolean superAdmin, Map<String, ModuleAccess> modules, Set<String> actionCodes) {

    public static final String READ = "READ";
    public static final String WRITE = "WRITE";
    public static final String DELETE = "DELETE";
    public static final String EXPORT = "EXPORT";
    public static final String APPROVE = "APPROVE";

    public EffectivePermissions {
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(modules));
        actionCodes = Collections.unmodifiableSet(new LinkedHashSet<>(actionCodes));
    }

    public static String authority(String module, String level) {
        return module + ":" + level;
    }

    public boolean canRead(String module) {
        return modules.containsKey(module);
    }

    public boolean canWrite(String module) {
        ModuleAccess access = modules.get(module);
        return access != null && access.write();
    }

    /** Generic (DELETE/EXPORT/APPROVE) or module-specific action on a module. */
    public boolean canPerform(String module, String action) {
        ModuleAccess access = modules.get(module);
        if (access == null) {
            return false;
        }
        return switch (action) {
            case READ -> true;
            case WRITE -> access.write();
            case DELETE -> access.delete();
            case EXPORT -> access.export();
            case APPROVE -> access.approve();
            default -> actionCodes.contains(authority(module, action));
        };
    }

    public Optional<DataScope> scope(String module) {
        return Optional.ofNullable(modules.get(module)).map(ModuleAccess::scope);
    }

    /** Spring Security authorities derived from the permissions. */
    public Set<String> authorities() {
        Set<String> authorities = new LinkedHashSet<>();
        modules.forEach((module, access) -> {
            authorities.add(authority(module, READ));
            if (access.write()) {
                authorities.add(authority(module, WRITE));
            }
            if (access.delete()) {
                authorities.add(authority(module, DELETE));
            }
            if (access.export()) {
                authorities.add(authority(module, EXPORT));
            }
            if (access.approve()) {
                authorities.add(authority(module, APPROVE));
            }
        });
        authorities.addAll(actionCodes);
        return authorities;
    }
}
