package com.pharmacy.stock.modules.product.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.product.dto.ProductDto;
import com.pharmacy.stock.modules.product.dto.ProductFormDto;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> findAll() {
        return productRepository.findAllByOrderByDesignationAsc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return findAll();
        }
        String normalized = keyword.trim().toLowerCase();
        return productRepository
                .findByCodeContainingIgnoreCaseOrDesignationContainingIgnoreCase(normalized, normalized)
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findById(Long id) {
        return productRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDto> findByCode(String code) {
        return productRepository.findByCode(code).map(this::toDto);
    }

    @Override
    @Transactional
    public ProductDto createProduct(ProductFormDto form) {
        if (productRepository.existsByCodeIgnoreCase(form.getCode())) {
            throw new BusinessException("Un produit existe déjà avec le code : " + form.getCode());
        }
        Product product = new Product();
        applyForm(product, form);
        return toDto(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductDto updateProduct(Long id, ProductFormDto form) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Produit introuvable : id=" + id));

        productRepository.findByCodeIgnoreCase(form.getCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException("Un autre produit utilise déjà le code : " + form.getCode());
                });

        applyForm(product, form);
        return toDto(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return productRepository.existsByCodeIgnoreCase(code);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return productRepository.count();
    }

    private void applyForm(Product product, ProductFormDto form) {
        product.setCode(form.getCode().trim().toUpperCase());
        product.setDesignation(form.getDesignation().trim());
        product.setUnit(form.getUnit().trim());
        product.setUnitPrice(form.getUnitPrice() == null ? BigDecimal.ZERO : form.getUnitPrice());
    }

    private ProductDto toDto(Product product) {
        return new ProductDto(
                product.getId(),
                product.getCode(),
                product.getDesignation(),
                product.getUnit(),
                product.getUnitPrice(),
                product.getCreatedAt());
    }
}
