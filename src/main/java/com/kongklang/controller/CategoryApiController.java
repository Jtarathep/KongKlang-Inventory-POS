package com.kongklang.controller;

import com.kongklang.entity.Category;
import com.kongklang.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryApiController {
    private final CategoryService categoryService;
    public CategoryApiController(CategoryService categoryService) { this.categoryService = categoryService; }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories(HttpSession session) {
        return ResponseEntity.ok(categoryService.getAllCategories(session));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody Category category, HttpSession session) {
        Category saved = categoryService.saveCategory(category, session);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "name", saved.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(@PathVariable Long id, @RequestBody Category category, HttpSession session) {
        category.setId(id);
        Category saved = categoryService.saveCategory(category, session);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "name", saved.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, HttpSession session) {
        categoryService.deleteCategory(id, session);
        return ResponseEntity.noContent().build();
    }
}
