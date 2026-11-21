package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class LegacyInventoryController {
    private final AccessService access;private final LegacyInventoryReview review;private final ProductService products;
    public LegacyInventoryController(AccessService access,LegacyInventoryReview review,ProductService products){this.access=access;this.review=review;this.products=products;}
    @GetMapping("/stock/legacy-review") public String page(HttpSession session,Model model){access.require(session,"OWNER","ADMIN");model.addAttribute("products",products.getAllProducts(session));return "legacy-review";}
    @PostMapping("/api/stock/legacy-review") @ResponseBody
    public InventoryOperationService.OperationView confirm(@RequestBody LegacyInventoryReview.Input input,HttpSession session){return review.confirm(access.require(session,"OWNER","ADMIN"),input);}
}
