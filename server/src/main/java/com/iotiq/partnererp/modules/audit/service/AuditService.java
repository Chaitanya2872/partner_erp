package com.iotiq.partnererp.modules.audit.service;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes the append-only audit trail. Entries join the caller's transaction, so an audited change and its
 * entry commit or roll back together; {@link #recordIndependently} is for events that must be kept even
 * when the request fails (rejected logins). Keys that look like secrets are replaced with "[REDACTED]"
 * before anything is stored.
 */
@Service
public class AuditService {

    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|token|secret|hash|credential).*");
    private static final String REDACTED = "[REDACTED]";

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Records an event performed by the signed-in user (if any). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, UUID entityId, Object oldValues, Object newValues) {
        save(SecurityUtils.currentUserId().orElse(null), action, entityType, entityId, oldValues, newValues);
    }

    /** Records an event with an explicit actor (e.g. a login, where nobody is signed in yet). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAs(UUID actorId, String action, String entityType, UUID entityId, Object oldValues,
                         Object newValues) {
        save(actorId, action, entityType, entityId, oldValues, newValues);
    }

    /** Commits on its own, so the entry survives the caller's rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(UUID actorId, String action, String entityType, UUID entityId,
                                    Object oldValues, Object newValues) {
        save(actorId, action, entityType, entityId, oldValues, newValues);
    }

    private void save(UUID actorId, String action, String entityType, UUID entityId, Object oldValues,
                      Object newValues) {
        AuditLog entry = new AuditLog();
        entry.setUserId(actorId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValues(toJson(oldValues));
        entry.setNewValues(toJson(newValues));
        entry.setRequestId(MDC.get(AppConstants.REQUEST_ID_MDC_KEY));
        entry.setIpAddress(clientIp());
        repository.save(entry);
    }

    private String toJson(Object values) {
        if (values == null) {
            return null;
        }
        return objectMapper.writeValueAsString(redact(objectMapper.convertValue(values, Object.class)));
    }

    @SuppressWarnings("unchecked")
    static Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                String key = String.valueOf(k);
                copy.put(key, SECRET_KEY.matcher(key).matches() ? REDACTED : redact(v));
            });
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AuditService::redact).toList();
        }
        return value;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getRemoteAddr();
            return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
        }
        return null;
    }

    /** Ordered key/value map for before/after snapshots ({@code values("email", a, "role", b)}). */
    public static Map<String, Object> values(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
