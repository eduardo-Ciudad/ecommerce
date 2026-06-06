package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.input.productvariant.ProductVariantInput;
import com.eduardo.ecomerce.dto.output.productvariant.ProductVariantOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    public ProductVariantOutput create(UUID productId, ProductVariantInput input) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize(input.size());
        variant.setPrice(input.price());
        variant.setStock(input.stock());
        productVariantRepository.save(variant);
        return toOutput(variant);
    }

    public ProductVariantOutput update(UUID id, ProductVariantInput input) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variante não encontrada"));
        variant.setSize(input.size());
        variant.setPrice(input.price());
        variant.setStock(input.stock());
        productVariantRepository.save(variant);
        return toOutput(variant);
    }

    public void delete(UUID id) {
        if (!productVariantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Variante não encontrada");
        }
        productVariantRepository.deleteById(id);
    }

    private ProductVariantOutput toOutput(ProductVariant variant) {
        return new ProductVariantOutput(
                variant.getId(),
                variant.getSize(),
                variant.getPrice(),
                variant.getStock(),
                variant.getCreatedAt()
        );
    }
}
