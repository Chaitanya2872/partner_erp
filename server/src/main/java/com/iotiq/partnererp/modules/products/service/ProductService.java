package com.iotiq.partnererp.modules.products.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.entity.AuditLog;
import com.iotiq.partnererp.modules.audit.repository.AuditLogRepository;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.products.dto.ProductHistoryEntry;
import com.iotiq.partnererp.modules.products.dto.ProductMeta;
import com.iotiq.partnererp.modules.products.dto.ProductOption;
import com.iotiq.partnererp.modules.products.dto.ProductRequest;
import com.iotiq.partnererp.modules.products.dto.ProductStatusRequest;
import com.iotiq.partnererp.modules.products.dto.ProductView;
import com.iotiq.partnererp.modules.products.entity.Product;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.mapper.ProductMapper;
import com.iotiq.partnererp.modules.products.repository.ProductMetricsRepository;
import com.iotiq.partnererp.modules.products.repository.ProductRepository;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Item & price sheet (BUSINESS_RULES §5). Internal fields (B2C name, alternative names, B2C price, landed cost)
 * are read and written only with {@code PRODUCTS:VIEW_INTERNAL_B2C}; because the B2C name is required, creating a
 * product needs that permission too (P7-04). Products are deactivated rather than deleted; hard delete is allowed
 * only for products no document line has referenced (D-17).
 */
@Service
public class ProductService {

    public static final String INTERNAL_ACTION = "VIEW_INTERNAL_B2C";
    /** Fallback when the company settings row is missing (BR-I3 default). */
    static final BigDecimal DEFAULT_GST = new BigDecimal("18.000");
    static final int OPTIONS_LIMIT = 50;

    private final ProductRepository repository;
    private final ProductMetricsRepository metrics;
    private final CompanySettingsRepository settingsRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProductService(ProductRepository repository, ProductMetricsRepository metrics,
                          CompanySettingsRepository settingsRepository, AuditLogRepository auditLogRepository,
                          UserRepository userRepository, AuditService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.metrics = metrics;
        this.settingsRepository = settingsRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** List filters; any may be null. {@code b2cMissing} is ignored for callers without the internal action. */
    public record Filters(String query, ProductCategory category, Boolean active, Boolean b2cMissing) {
    }

    // ------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<ProductView> search(Filters filters, Pageable pageable, AuthenticatedUser user) {
        boolean internal = canViewInternal(user);
        return repository.findAll(matching(filters, internal), pageable).map(mapper(internal));
    }

    /** Every product matching the filters in sheet order (category, SKU), for the CSV export. */
    @Transactional(readOnly = true)
    public List<Product> searchAll(Filters filters, AuthenticatedUser user) {
        return repository.findAll(matching(filters, canViewInternal(user)), Sort.by("category", "sku"));
    }

    @Transactional(readOnly = true)
    public ProductView get(UUID id, AuthenticatedUser user) {
        return mapper(canViewInternal(user)).apply(load(id));
    }

    /** Active products for document line pickers, best matches first by sheet order; at most 50. */
    @Transactional(readOnly = true)
    public List<ProductOption> options(String query, ProductCategory category, AuthenticatedUser user) {
        boolean internal = canViewInternal(user);
        return repository.findAll(matching(new Filters(query, category, true, null), internal),
                        PageRequest.of(0, OPTIONS_LIMIT, Sort.by("category", "sku")))
                .map(p -> ProductMapper.toOption(p, internal)).getContent();
    }

    /**
     * The values a new estimate or invoice line copies (Phase 8/9 entry point). Inactive products can't be added
     * to new documents (D-17); lines already saved keep their snapshot.
     */
    @Transactional(readOnly = true)
    public ProductLineSnapshot lineSnapshot(UUID id) {
        Product product = load(id);
        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_INACTIVE",
                    "Product " + product.getSku() + " is deactivated and can't be added to new documents.");
        }
        return ProductMapper.toLineSnapshot(product);
    }

    @Transactional(readOnly = true)
    public ProductMeta meta() {
        Map<ProductCategory, ProductMetricsRepository.Counts> counts = metrics.categoryCounts();
        return new ProductMeta(Arrays.stream(ProductCategory.values()).map(c -> {
            ProductMetricsRepository.Counts n = counts.getOrDefault(c, new ProductMetricsRepository.Counts(0, 0));
            return new ProductMeta.Category(c.name(), c.getLabel(), n.total(), n.active());
        }).toList(), defaultGst(), ProductRules.DEFAULT_UNIT, ProductRules.MAX_ALIASES);
    }

    /** Audited changes, newest first (at most 200). Internal fields are removed unless the caller may see them. */
    @Transactional(readOnly = true)
    public List<ProductHistoryEntry> history(UUID id, AuthenticatedUser user) {
        load(id);
        boolean internal = canViewInternal(user);
        List<AuditLog> entries = auditLogRepository.findTop200ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                AuditActions.ENTITY_PRODUCT, id);
        Set<UUID> actorIds = entries.stream().map(AuditLog::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        List<ProductHistoryEntry> history = new ArrayList<>();
        for (AuditLog entry : entries) {
            List<ProductHistoryEntry.Change> changes = changes(entry, internal);
            // An update that touched only internal fields is not shown to callers who can't see them.
            if (changes.isEmpty() && (AuditActions.PRODUCT_UPDATED.equals(entry.getAction())
                    || AuditActions.PRODUCT_IMPORTED.equals(entry.getAction()) && entry.getOldValues() != null)) {
                continue;
            }
            history.add(new ProductHistoryEntry(entry.getId(), entry.getCreatedAt(),
                    entry.getUserId() == null ? null : names.get(entry.getUserId()), entry.getAction(), changes));
        }
        return history;
    }

    // ------------------------------------------------------------------------------------------- commands

    @Transactional
    public ProductView create(ProductRequest request, AuthenticatedUser user) {
        if (!canViewInternal(user)) {
            throw internalRequired("Creating a product needs the View internal B2C prices permission, because the "
                    + "B2C name is required.");
        }
        Normalized input = normalize(request, true);
        checkSkuFree(input.sku(), null);
        Product product = new Product();
        apply(product, input);
        applyInternal(product, input);
        product.setActive(true);
        repository.saveAndFlush(product);
        auditService.record(AuditActions.PRODUCT_CREATED, AuditActions.ENTITY_PRODUCT, product.getId(), null,
                ProductMapper.auditSnapshot(product));
        return ProductMapper.toInternalView(product);
    }

    @Transactional
    public ProductView update(UUID id, ProductRequest request, AuthenticatedUser user) {
        boolean internal = canViewInternal(user);
        if (!internal && request.hasInternalFields()) {
            throw internalRequired("Changing the B2C name, alternative names, B2C price or landed cost needs the "
                    + "View internal B2C prices permission.");
        }
        Product product = load(id);
        Normalized input = normalize(request, internal);
        if (request.version() == null) {
            throw ValidationException.of("version", "Version is required.");
        }
        if (request.version() != product.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Product.class, id);
        }
        checkSkuFree(input.sku(), id);
        Map<String, Object> before = ProductMapper.auditSnapshot(product);
        apply(product, input);
        if (internal) {
            applyInternal(product, input);
        }
        Map<String, Object> after = ProductMapper.auditSnapshot(product);
        Set<String> changed = changedKeys(before, after);
        if (!changed.isEmpty()) {
            repository.saveAndFlush(product);
            auditService.record(AuditActions.PRODUCT_UPDATED, AuditActions.ENTITY_PRODUCT, id,
                    only(before, changed), only(after, changed));
        }
        return mapper(internal).apply(product);
    }

    @Transactional
    public ProductView changeStatus(UUID id, ProductStatusRequest request, AuthenticatedUser user) {
        Product product = repository.lock(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (request.version() != product.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Product.class, id);
        }
        if (product.isActive() != request.active()) {
            product.setActive(request.active());
            repository.saveAndFlush(product);
            auditService.record(AuditActions.PRODUCT_STATUS_CHANGED, AuditActions.ENTITY_PRODUCT, id,
                    AuditService.values("active", !request.active()), AuditService.values("active", request.active()));
        }
        return mapper(canViewInternal(user)).apply(product);
    }

    /** D-17: only products that no estimate, invoice or B2C document line has used can be removed. */
    @Transactional
    public void delete(UUID id) {
        Product product = repository.lock(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (metrics.isReferenced(id)) {
            throw BusinessException.conflict("PRODUCT_IN_USE",
                    "This product is used on estimates or invoices, so it can't be deleted. Deactivate it instead.");
        }
        Map<String, Object> before = ProductMapper.auditSnapshot(product);
        repository.delete(product);
        repository.flush();
        auditService.record(AuditActions.PRODUCT_DELETED, AuditActions.ENTITY_PRODUCT, id, before, null);
    }

    // ------------------------------------------------------------------------------------------- helpers

    public static boolean canViewInternal(AuthenticatedUser user) {
        return user.permissions().canPerform(ModuleCode.PRODUCTS.name(), INTERNAL_ACTION);
    }

    static BusinessException internalRequired(String message) {
        return new BusinessException("INTERNAL_B2C_REQUIRED", message, HttpStatus.FORBIDDEN);
    }

    BigDecimal defaultGst() {
        return settingsRepository.findSingleton().map(CompanySettings::getDefaultGstPercent).orElse(DEFAULT_GST);
    }

    Product load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private void checkSkuFree(String sku, UUID self) {
        repository.findBySkuIgnoreCase(sku).filter(p -> !p.getId().equals(self)).ifPresent(p -> {
            throw ValidationException.of("sku", "That SKU already exists.");
        });
    }

    private static Function<Product, ProductView> mapper(boolean internal) {
        return internal ? ProductMapper::toInternalView : ProductMapper::toPartnerView;
    }

    static Set<String> changedKeys(Map<String, Object> before, Map<String, Object> after) {
        Set<String> changed = new LinkedHashSet<>();
        before.keySet().forEach(k -> {
            if (!same(before.get(k), after.get(k))) {
                changed.add(k);
            }
        });
        return changed;
    }

    /** Equality that treats 18 and 18.000 as the same amount. */
    private static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    /** The entries of {@code snapshot} for {@code keys}, in snapshot order. */
    static Map<String, Object> only(Map<String, Object> snapshot, Set<String> keys) {
        Map<String, Object> out = AuditService.values();
        snapshot.forEach((k, v) -> {
            if (keys.contains(k)) {
                out.put(k, v);
            }
        });
        return out;
    }

    private List<ProductHistoryEntry.Change> changes(AuditLog entry, boolean internal) {
        JsonNode before = parse(entry.getOldValues());
        JsonNode after = parse(entry.getNewValues());
        Set<String> fields = new LinkedHashSet<>();
        before.propertyNames().forEach(fields::add);
        after.propertyNames().forEach(fields::add);
        List<ProductHistoryEntry.Change> changes = new ArrayList<>();
        for (String field : fields) {
            if (!internal && ProductMapper.INTERNAL_FIELDS.contains(field)) {
                continue;
            }
            changes.add(new ProductHistoryEntry.Change(field, before.path(field).isMissingNode() ? null : before.get(field),
                    after.path(field).isMissingNode() ? null : after.get(field)));
        }
        return changes;
    }

    private JsonNode parse(String json) {
        return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
    }

    private static Specification<Product> matching(Filters f, boolean internal) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.query() != null && !f.query().isBlank()) {
                String q = f.query().trim().toLowerCase(Locale.ROOT);
                String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("sku")), like, '\\'),
                        cb.like(cb.lower(root.get("partnerName")), like, '\\')));
                if (internal) {
                    // Only internal users may find products by their B2C name.
                    any.add(cb.like(cb.lower(root.get("b2cName")), like, '\\'));
                }
                // BR-I8: the search also covers the category, by its displayed label.
                List<ProductCategory> categories = Arrays.stream(ProductCategory.values())
                        .filter(c -> c.getLabel().toLowerCase(Locale.ROOT).contains(q)).toList();
                if (!categories.isEmpty()) {
                    any.add(root.get("category").in(categories));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (f.category() != null) {
                predicates.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.active() != null) {
                predicates.add(cb.equal(root.get("active"), f.active()));
            }
            if (internal && f.b2cMissing() != null) {
                Predicate missing = cb.or(cb.isNull(root.get("b2cPrice")), cb.equal(root.get("b2cPrice"), BigDecimal.ZERO));
                predicates.add(f.b2cMissing() ? missing : cb.not(missing));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void apply(Product product, Normalized input) {
        product.setSku(input.sku());
        product.setCategory(input.category());
        product.setUnit(input.unit());
        product.setHsn(input.hsn());
        product.setPartnerName(input.partnerName());
        product.setPartnerPrice(input.partnerPrice());
        product.setGstPercent(input.gstPercent());
    }

    private static void applyInternal(Product product, Normalized input) {
        product.setB2cName(input.b2cName());
        product.setAliases(new ArrayList<>(input.aliases()));
        product.setB2cPrice(input.b2cPrice());
        product.setCostPrice(input.costPrice());
    }

    // ------------------------------------------------------------------------------------------- validation

    record Normalized(String sku, ProductCategory category, String unit, String hsn, String partnerName,
                      BigDecimal partnerPrice, BigDecimal gstPercent, String b2cName, List<String> aliases,
                      BigDecimal b2cPrice, BigDecimal costPrice) {
    }

    /** Validates every field; internal fields only when {@code internal} (they are ignored otherwise). */
    private static Normalized normalize(ProductRequest r, boolean internal) {
        List<FieldError> errors = new ArrayList<>();
        String sku = ProductRules.sku(r.sku(), errors);
        if (r.category() == null) {
            errors.add(new FieldError("category", "Category is required."));
        }
        String unit = ProductRules.unit(r.unit(), errors);
        String hsn = ProductRules.hsn(r.hsn(), errors);
        String partnerName = ProductRules.requiredName(r.partnerName(), "partnerName", "Partner-facing name", errors);
        BigDecimal partnerPrice = ProductRules.amount(r.partnerPrice(), "partnerPrice", errors);
        if (r.partnerPrice() == null) {
            errors.add(new FieldError("partnerPrice", "Partner price is required."));
        }
        BigDecimal gst = ProductRules.gst(r.gstPercent(), errors);
        String b2cName = null;
        List<String> aliases = List.of();
        BigDecimal b2cPrice = null;
        BigDecimal costPrice = null;
        if (internal) {
            b2cName = ProductRules.requiredName(r.b2cName(), "b2cName", "B2C name", errors);
            aliases = ProductRules.aliases(r.aliases(), errors);
            b2cPrice = ProductRules.amount(r.b2cPrice(), "b2cPrice", errors);
            costPrice = ProductRules.amount(r.costPrice(), "costPrice", errors);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Normalized(sku, r.category(), unit, hsn, partnerName, partnerPrice, gst, b2cName, aliases,
                b2cPrice, costPrice);
    }
}
