package com.kongklang.controller;

import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

@Controller
@RequestMapping("/settings/tax")
public class TaxSettingsController {
    private final AccessService access;private final StoreTaxService taxes;
    public TaxSettingsController(AccessService access,StoreTaxService taxes){this.access=access;this.taxes=taxes;}
    @GetMapping public String page(HttpSession session,Model model){var actor=access.require(session,"OWNER","ADMIN");model.addAttribute("tax",taxes.read(actor.storeId()));return "tax-settings";}
    @PostMapping public String save(@RequestParam String mode,@RequestParam BigDecimal rate,
            @RequestParam(defaultValue="false") boolean registered,@RequestParam(defaultValue="") String legalName,
            @RequestParam(defaultValue="") String legalAddress,@RequestParam(defaultValue="") String taxId,
            @RequestParam(defaultValue="") String branchCode,@RequestParam long revision,
            HttpSession session,Model model,RedirectAttributes redirect){
        var actor=access.require(session,"OWNER","ADMIN");var input=new StoreTaxService.Input(mode,rate,registered,legalName,legalAddress,taxId,branchCode,revision);
        try{taxes.save(actor,input);redirect.addFlashAttribute("success","บันทึกการตั้งค่าภาษีแล้ว มีผลเฉพาะบิลใหม่");return "redirect:/settings/tax";}
        catch(IllegalArgumentException e){model.addAttribute("tax",input);model.addAttribute("error",e.getMessage());return "tax-settings";}
    }
}
