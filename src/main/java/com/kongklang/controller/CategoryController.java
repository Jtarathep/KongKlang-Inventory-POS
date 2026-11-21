package com.kongklang.controller;

import com.kongklang.entity.Category;
import com.kongklang.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        model.addAttribute("categories", categoryService.getAllCategories(session));
        model.addAttribute("category", new Category());
        return "categories";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String name,
                       HttpSession session,
                       RedirectAttributes redirect) {
        try {
            Category category;
            if (id == null) {
                category = new Category();
            } else {
                category = categoryService.getCategoryById(id, session);
                if (category == null) {
                    redirect.addFlashAttribute("error", "ไม่พบหมวดหมู่ในร้านปัจจุบัน");
                    return "redirect:/categories";
                }
            }
            category.setName(name);
            categoryService.saveCategory(category, session);
            redirect.addFlashAttribute("success",
                    id == null ? "เพิ่มหมวดหมู่เรียบร้อยแล้ว" : "แก้ไขหมวดหมู่เรียบร้อยแล้ว");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
                         HttpSession session,
                         RedirectAttributes redirect) {
        try {
            Category category = categoryService.getCategoryById(id, session);
            if (category == null) {
                redirect.addFlashAttribute("error", "ไม่พบหมวดหมู่ในร้านปัจจุบัน");
            } else {
                categoryService.deleteCategory(id, session);
                redirect.addFlashAttribute("success", "ลบหมวดหมู่เรียบร้อยแล้ว");
            }
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", "ไม่สามารถลบหมวดหมู่นี้ได้ อาจมีสินค้าใช้งานอยู่");
        }
        return "redirect:/categories";
    }
}
