package com.example.inventoryservice.controller;

import com.example.inventoryservice.dto.InventoryReserveRequest;
import com.example.inventoryservice.dto.InventoryReserveResponse;
import com.example.inventoryservice.entity.Product;
import com.example.inventoryservice.exception.InsufficientStockException;
import com.example.inventoryservice.exception.ProductNotFoundException;
import com.example.inventoryservice.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ProductRepository productRepository;

    public InventoryController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostMapping("/reserve")
    public ResponseEntity<InventoryReserveResponse> reserve(@RequestBody InventoryReserveRequest request) {
        Product product = findProductOrThrow(request.getProductCode());

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(
                    "insufficient stock for " + request.getProductCode() + ": requested "
                            + request.getQuantity() + ", available " + product.getStockQuantity());
        }

        product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        productRepository.save(product);

        return ResponseEntity.ok(new InventoryReserveResponse(
                product.getProductCode(), request.getQuantity(), product.getStockQuantity()));
    }

    @PostMapping("/release")
    public ResponseEntity<InventoryReserveResponse> release(@RequestBody InventoryReserveRequest request) {
        Product product = findProductOrThrow(request.getProductCode());

        product.setStockQuantity(product.getStockQuantity() + request.getQuantity());
        productRepository.save(product);

        return ResponseEntity.ok(new InventoryReserveResponse(
                product.getProductCode(), request.getQuantity(), product.getStockQuantity()));
    }

    @GetMapping("/{productCode}")
    public ResponseEntity<Product> getProduct(@PathVariable String productCode) {
        return productRepository.findById(productCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Product findProductOrThrow(String productCode) {
        return productRepository.findById(productCode)
                .orElseThrow(() -> new ProductNotFoundException("unknown product code: " + productCode));
    }
}
