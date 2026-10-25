package com.iotiq.partnererp.modules.products.service;

import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.modules.products.dto.ProductView;
import com.iotiq.partnererp.modules.products.entity.Product;
import com.iotiq.partnererp.modules.products.entity.ProductCategory;
import com.iotiq.partnererp.modules.products.mapper.ProductMapper;
import com.iotiq.partnererp.modules.products.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read-only price sheet (Phase 3 subset; product maintenance arrives in Phase 7). The caller decides which
 * view is returned based on {@code PRODUCTS:VIEW_INTERNAL_B2C}.
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ProductView> search(String query, ProductCategory category, boolean internal, Pageable pageable) {
        Specification<Product> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("sku")), like, '\\'),
                        cb.like(cb.lower(root.get("partnerName")), like, '\\')));
                if (internal) {
                    // Only internal users may find products by their B2C name.
                    any.add(cb.like(cb.lower(root.get("b2cName")), like, '\\'));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable).map(mapper(internal));
    }

    @Transactional(readOnly = true)
    public ProductView get(UUID id, boolean internal) {
        return repository.findById(id).map(mapper(internal))
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private static Function<Product, ProductView> mapper(boolean internal) {
        return internal ? ProductMapper::toInternalView : ProductMapper::toPartnerView;
    }
}
