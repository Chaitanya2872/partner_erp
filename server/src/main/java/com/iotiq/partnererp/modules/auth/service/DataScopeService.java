package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Turns a user's data scope for a module into a query restriction. Business list/detail queries of scoped
 * modules (partners, leads, estimates, invoices, payments, payouts) must apply it.
 *
 * <p>Limitation: records have no owner or team yet (owner columns are pending decision D-30, and there is
 * no team model). SELF and TEAM therefore cannot be evaluated and resolve to "no records" rather than
 * silently widening to ALL.
 */
@Service
public class DataScopeService {

    public ScopeFilter resolve(AuthenticatedUser user, ModuleCode module) {
        DataScope scope = user.permissions().scope(module.name())
                .orElseThrow(() -> new AccessDeniedException("No access to " + module));
        return switch (scope) {
            case ALL -> ScopeFilter.UNRESTRICTED;
            case SELF -> new ScopeFilter(false, "SELF scope needs record ownership, which is not modelled yet (D-30)");
            case TEAM -> new ScopeFilter(false, "TEAM scope needs a team model, which does not exist yet");
        };
    }

    /**
     * @param unrestricted true when every record of the module is visible
     * @param reason       why records are hidden, when they are
     */
    public record ScopeFilter(boolean unrestricted, String reason) {

        public static final ScopeFilter UNRESTRICTED = new ScopeFilter(true, null);

        /** A specification to combine with the module's own query. */
        public <T> Specification<T> toSpecification() {
            return (root, query, cb) -> unrestricted ? cb.conjunction() : cb.disjunction();
        }
    }
}
