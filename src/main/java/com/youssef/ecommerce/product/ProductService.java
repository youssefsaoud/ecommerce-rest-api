package com.youssef.ecommerce.product;

import com.youssef.ecommerce.category.Category;
import com.youssef.ecommerce.category.CategoryRepository;
import com.youssef.ecommerce.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository, EntityManager entityManager) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        updateProductFields(product, request);

        return productRepository.save(product);
    }

    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest product) {
        Product existingProduct = getProductById(id);
        entityManager.refresh(existingProduct, LockModeType.PESSIMISTIC_WRITE);
        updateProductFields(existingProduct, product);

        return productRepository.save(existingProduct);
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found");
        }

        productRepository.deleteById(id);
    }

    private void updateProductFields(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());

        if (request.categoryId() == null) {
            product.setCategory(null);
        } else {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            product.setCategory(category);
        }
    }
}
