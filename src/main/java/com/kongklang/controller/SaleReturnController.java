package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@Controller
public class SaleReturnController {
    private final AccessService access;private final SaleReturnService returns;private final SaleService sales;
    public SaleReturnController(AccessService access,SaleReturnService returns,SaleService sales){this.access=access;this.returns=returns;this.sales=sales;}
    @GetMapping("/sales/{id}/returns") public String page(@PathVariable Long id,HttpSession session,Model model){var actor=access.require(session,"OWNER","ADMIN","ACCOUNTING");var sale=sales.getSaleById(id,actor.storeId());if(sale==null)return "redirect:/sales/history";model.addAttribute("sale",sale);model.addAttribute("returnable",returns.available(id,actor.storeId()));model.addAttribute("refunds",returns.list(id,actor.storeId()));return "sale-returns";}
    @PostMapping("/api/sales/{id}/returns/quote") @ResponseBody public ResponseEntity<?> quote(@PathVariable Long id,@RequestBody List<SaleReturnService.LineInput> lines,HttpSession session){try{return ResponseEntity.ok(returns.quote(access.require(session,"OWNER","ADMIN"),id,lines));}catch(IllegalArgumentException|ArithmeticException e){return ResponseEntity.badRequest().body(java.util.Map.of("message",e.getMessage()));}}
    @PostMapping("/api/sales/{id}/returns") @ResponseBody public ResponseEntity<?> refund(@PathVariable Long id,@RequestBody SaleReturnService.Input input,HttpSession session){try{return ResponseEntity.ok(returns.refund(access.require(session,"OWNER","ADMIN"),id,input));}catch(IllegalArgumentException|ArithmeticException e){return ResponseEntity.badRequest().body(java.util.Map.of("message",e.getMessage()));}}
}
