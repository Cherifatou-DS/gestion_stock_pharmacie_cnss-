package com.pharmacy.stock.modules.product.service;

import com.pharmacy.stock.modules.product.dto.ProductDto;
import com.pharmacy.stock.modules.product.dto.ProductFormDto;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<ProductDto> findAll();

    List<ProductDto> search(String keyword);

    Optional<ProductDto> findById(Long id);

    Optional<ProductDto> findByCode(String code);

    ProductDto createProduct(ProductFormDto form);

    ProductDto updateProduct(Long id, ProductFormDto form);

    boolean existsByCode(String code);

    long countAll();
}
