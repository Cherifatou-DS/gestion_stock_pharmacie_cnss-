package com.pharmacy.stock.modules.product.repository;

import com.pharmacy.stock.modules.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);

    Optional<Product> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Product> findAllByOrderByDesignationAsc();

    List<Product> findByCodeContainingIgnoreCaseOrDesignationContainingIgnoreCase(String code, String designation);
}
