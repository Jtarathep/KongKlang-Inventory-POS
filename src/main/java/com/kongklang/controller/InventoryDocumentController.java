package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@Controller
public class InventoryDocumentController {
    private final AccessService access;private final InventoryOperationService operations;private final LegacyInventoryReview review;
    public InventoryDocumentController(AccessService access,InventoryOperationService operations,LegacyInventoryReview review){this.access=access;this.operations=operations;this.review=review;}
    @GetMapping("/stock/operations")
    public String list(@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,
            @RequestParam(defaultValue="") String code,@RequestParam(defaultValue="0") int page,HttpSession session,Model model){
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));if(start==null)start=today.minusMonths(1);if(end==null)end=today;
        model.addAttribute("documents",operations.search(access.require(session,"OWNER","ADMIN","WAREHOUSE","ACCOUNTING"),start,end,code,page));
        model.addAttribute("start",start);model.addAttribute("end",end);model.addAttribute("code",code);return "inventory-documents";
    }
    @GetMapping("/stock/operations/{id}") public String detail(@PathVariable Long id,HttpSession session,Model model){
        var actor=access.require(session,"OWNER","ADMIN","WAREHOUSE","ACCOUNTING");
        model.addAttribute("document",operations.detail(actor,id));model.addAttribute("legacySnapshot",review.snapshot(actor.storeId(),id));return "inventory-document";
    }
}
