package com.iotiq.partnererp.modules.audit.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.modules.audit.dto.AuditLogResponse;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.products.service.ProductCsv;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public static final int EXPORT_LIMIT = 10_000;
    static final int MAX_SEARCH = 100;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Kolkata"));

    /**
     * Viewer filters. {@code q} matches the action, entity type, the actor's name or email, and an entity or request id
     * given in full. It never searches the recorded values, so masked or hidden fields can't be probed through it.
     */
    public record Filter(String module, String entityType, UUID entityId, String action, UUID userId, Instant from,
                         Instant to, String q) {

        public static Filter entity(String entityType, UUID entityId) {
            return new Filter(null, entityType, entityId, null, null, null, null, null);
        }
    }

    public record Actor(UUID id, String fullName, String email) {
    }

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
    public Page<AuditLogResponse> search(Filter filter, Pageable pageable, boolean internalProductFields) {
        Page<AuditLog> page = repository.findAll(spec(filter), pageable);
        Map<UUID, User> actors = actors(page.getContent());
        return page.map(e -> toResponse(e, actors, internalProductFields));
    }

    /** CSV of every matching entry, newest first (AUDIT_LOGS:EXPORT), with the same hiding rules as the viewer. */
    @Transactional(readOnly = true)
    public Export export(Filter filter, boolean internalProductFields) {
        Specification<AuditLog> spec = spec(filter);
        long total = repository.count(spec);
        if (total > EXPORT_LIMIT) {
            throw new BusinessException("AUDIT_EXPORT_TOO_LARGE", "The selection has " + total
                    + " entries; an export holds at most " + EXPORT_LIMIT + ". Narrow the dates or filters and try again.",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        List<AuditLog> entries = total == 0 ? List.of()
                : repository.findAll(spec, PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        Map<UUID, User> actors = actors(entries);
        List<List<String>> rows = new ArrayList<>();
        for (AuditLog e : entries) {
            AuditLogResponse r = toResponse(e, actors, internalProductFields);
            rows.add(List.of(CSV_TIME.format(r.createdAt()), nz(r.actorName()), nz(r.actorEmail()), r.action(),
                    AuditCatalog.moduleOf(r.action()).map(AuditCatalog.Module::label).orElse(""), r.entityType(),
                    r.entityId() == null ? "" : r.entityId().toString(), nz(r.ipAddress()), nz(r.requestId()),
                    r.oldValues() == null ? "" : r.oldValues().toString(),
                    r.newValues() == null ? "" : r.newValues().toString()));
        }
        String csv = ProductCsv.write(List.of("Time (IST)", "User", "User email", "Action", "Module", "Entity type",
                "Entity ID", "IP address", "Request ID", "Old values", "New values"), rows);
        return new Export(csv, entries.size());
    }

    public record Export(String csv, int rows) {
    }

    /** People who appear as actors, for the viewer's user filter (names and emails only). */
    @Transactional(readOnly = true)
    public List<Actor> actors(String query) {
        String like = like(query);
        Specification<User> spec = (root, cq, cb) -> like == null ? cb.conjunction()
                : cb.or(cb.like(cb.lower(root.get("fullName")), like, '\\'), cb.like(cb.lower(root.get("email")), like, '\\'));
        return userRepository.findAll(spec, PageRequest.of(0, 20, Sort.by("fullName"))).stream()
                .map(u -> new Actor(u.getId(), u.getFullName(), u.getEmail())).toList();
    }

    private Specification<AuditLog> spec(Filter f) {
        String q = f.q() == null ? null : f.q().strip();
        if (q != null && q.length() > MAX_SEARCH) {
            throw ValidationException.of("q", "Search with at most " + MAX_SEARCH + " characters.");
        }
        List<String> moduleActions = null;
        if (f.module() != null && !f.module().isBlank()) {
            moduleActions = AuditCatalog.module(f.module().strip())
                    .orElseThrow(() -> ValidationException.of("module", "Unknown module.")).actions();
        }
        if (f.from() != null && f.to() != null && !f.from().isBefore(f.to())) {
            throw ValidationException.of("to", "The end must be after the start.");
        }
        List<String> actions = moduleActions;
        String like = like(q);
        UUID qId = uuid(q);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actions != null) {
                predicates.add(root.get("action").in(actions));
            }
            if (f.entityType() != null && !f.entityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), f.entityType().strip().toUpperCase(Locale.ROOT)));
            }
            if (f.entityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), f.entityId()));
            }
            if (f.action() != null && !f.action().isBlank()) {
                predicates.add(cb.equal(root.get("action"), f.action().strip().toUpperCase(Locale.ROOT)));
            }
            if (f.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), f.userId()));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), f.to()));
            }
            if (like != null) {
                Subquery<UUID> people = query.subquery(UUID.class);
                var user = people.from(User.class);
                people.select(user.get("id")).where(cb.or(cb.like(cb.lower(user.get("fullName")), like, '\\'),
                        cb.like(cb.lower(user.get("email")), like, '\\')));
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("action")), like, '\\'),
                        cb.like(cb.lower(root.get("entityType")), like, '\\'),
                        root.get("userId").in(people),
                        cb.equal(root.get("requestId"), q)));
                if (qId != null) {
                    any.add(cb.equal(root.get("entityId"), qId));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<UUID, User> actors(List<AuditLog> entries) {
        List<UUID> actorIds = entries.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        return userRepository.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AuditLogResponse toResponse(AuditLog e, Map<UUID, User> actors, boolean internalProductFields) {
        User actor = e.getUserId() == null ? null : actors.get(e.getUserId());
        boolean hide = !internalProductFields && "PRODUCT".equals(e.getEntityType());
        return new AuditLogResponse(e.getId(), e.getCreatedAt(), e.getUserId(),
                actor == null ? null : actor.getFullName(), actor == null ? null : actor.getEmail(),
                e.getAction(), e.getEntityType(), e.getEntityId(), parse(e.getOldValues(), hide),
                parse(e.getNewValues(), hide), e.getIpAddress(), e.getRequestId());
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

    private static String like(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static UUID uuid(String q) {
        if (q == null) {
            return null;
        }
        try {
            return UUID.fromString(q.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
