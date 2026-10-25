package com.iotiq.partnererp.modules.audit.service;

import com.iotiq.partnererp.modules.audit.dto.AuditLogResponse;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuditLogQueryService {

    /** Product snapshot keys shown only to holders of {@code PRODUCTS:VIEW_INTERNAL_B2C} (P7-09). */
    static final Set<String> INTERNAL_PRODUCT_FIELDS = Set.of("b2cName", "aliases", "b2cPrice", "costPrice");

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogRepository repository, UserRepository userRepository,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param internalProductFields whether the caller may see B2C names, alternative names, B2C prices and landed
     *                              costs in product entries; without it those keys are removed
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String entityType, UUID entityId, String action, UUID userId,
                                         Instant from, Instant to, Pageable pageable,
                                         boolean internalProductFields) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim().toUpperCase()));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<AuditLog> page = repository.findAll(spec, pageable);
        List<UUID> actorIds = page.getContent().stream().map(AuditLog::getUserId).filter(Objects::nonNull)
                .distinct().toList();
        Map<UUID, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return page.map(e -> {
            User actor = e.getUserId() == null ? null : actors.get(e.getUserId());
            boolean hide = !internalProductFields && "PRODUCT".equals(e.getEntityType());
            return new AuditLogResponse(e.getId(), e.getCreatedAt(), e.getUserId(),
                    actor == null ? null : actor.getFullName(), actor == null ? null : actor.getEmail(),
                    e.getAction(), e.getEntityType(), e.getEntityId(), parse(e.getOldValues(), hide),
                    parse(e.getNewValues(), hide), e.getIpAddress(), e.getRequestId());
        });
    }

    private JsonNode parse(String json, boolean hideInternalProductFields) {
        if (json == null) {
            return null;
        }
        JsonNode node = objectMapper.readTree(json);
        if (hideInternalProductFields && node instanceof ObjectNode object) {
            INTERNAL_PRODUCT_FIELDS.forEach(object::remove);
        }
        return node;
    }
}
