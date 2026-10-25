package com.iotiq.partnererp.modules.products.repository;

import com.iotiq.partnererp.modules.products.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    /** Products whose lower-cased SKU is in {@code lowerSkus} (CSV import upsert). */
    @Query("select p from Product p where lower(p.sku) in :lowerSkus")
    List<Product> findBySkuLowerIn(Collection<String> lowerSkus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lock(UUID id);
}
