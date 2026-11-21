package com.kongklang.controller;

import com.kongklang.entity.Product;
import com.kongklang.service.ProductService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {


    private final ProductService productService;


    public ProductApiController(
            ProductService productService) {

        this.productService = productService;
    }


    // ==================================================
    // GET /api/products
    // สินค้าของ Store ปัจจุบัน
    // ==================================================

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts(
            HttpSession session) {

        return ResponseEntity.ok(
                productService.getAllProducts(session)
        );
    }


    // ==================================================
    // GET /api/products/{id}
    // ==================================================

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(
            @PathVariable Long id,
            HttpSession session) {

        Product product =
                productService.getProductById(
                        id,
                        session
                );


        if (product == null) {

            return ResponseEntity
                    .notFound()
                    .build();
        }


        return ResponseEntity.ok(product);
    }


    // ==================================================
    // GET /api/products/code/{code}
    // ==================================================

    @GetMapping("/code/{code}")
    public ResponseEntity<Product> getProductByCode(
            @PathVariable String code,
            HttpSession session) {

        Product product =
                productService.getProductByCode(
                        code,
                        session
                );


        if (product == null) {

            return ResponseEntity
                    .notFound()
                    .build();
        }


        return ResponseEntity.ok(product);
    }


    // ==================================================
    // POST /api/products
    // ==================================================

    @PostMapping
    public ResponseEntity<Product> createProduct(
            @RequestBody Product product,
            HttpSession session) {

        productService.saveProduct(
                product,
                session
        );


        Product savedProduct =
                productService.getProductByCode(
                        product.getCode(),
                        session
                );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedProduct);
    }


    // ==================================================
    // PUT /api/products/{id}
    // ==================================================

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody Product product,
            HttpSession session) {


        Product existingProduct =
                productService.getProductById(
                        id,
                        session
                );


        if (existingProduct == null) {

            return ResponseEntity
                    .notFound()
                    .build();
        }


        productService.updateProduct(
                id,
                product,
                session
        );


        return ResponseEntity.ok(
                productService.getProductById(
                        id,
                        session
                )
        );
    }


    // ==================================================
    // DELETE /api/products/{id}
    // ==================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            HttpSession session) {


        Product product =
                productService.getProductById(
                        id,
                        session
                );


        if (product == null) {

            return ResponseEntity
                    .notFound()
                    .build();
        }


        productService.deleteProduct(
                id,
                session
        );


        return ResponseEntity
                .noContent()
                .build();
    }
}