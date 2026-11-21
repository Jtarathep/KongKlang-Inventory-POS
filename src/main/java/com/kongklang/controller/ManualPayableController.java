package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/manual-invoices")
public class ManualPayableController {
    private final AccessService access;
    private final ManualPayableService service;
    public ManualPayableController(AccessService access,ManualPayableService service){this.access=access;this.service=service;}
    @PostMapping public PayableService.InvoiceView invoice(@RequestBody ManualPayableService.Input input,HttpSession session){
        return service.invoice(access.require(session,"OWNER","ADMIN","ACCOUNTING"),input);
    }
}
