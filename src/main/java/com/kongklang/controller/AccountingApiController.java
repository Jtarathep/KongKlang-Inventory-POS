package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/accounting")
public class AccountingApiController {
    private final AccessService access; private final PayableService payables; private final JournalService journals;
    public AccountingApiController(AccessService access,PayableService payables,JournalService journals) {this.access=access;this.payables=payables;this.journals=journals;}
    private AccessService.Actor actor(HttpSession session) {return access.require(session,"OWNER","ADMIN","ACCOUNTING");}
    @GetMapping("/invoices") public List<PayableService.InvoiceView> invoices(HttpSession session) {return payables.list(actor(session).storeId());}
    @PostMapping("/invoices") public PayableService.InvoiceView invoice(@RequestBody PayableService.InvoiceInput input,HttpSession session) {return payables.invoice(actor(session),input);}
    @PostMapping("/invoices/{id}/payments") public PayableService.InvoiceView pay(@PathVariable Long id,@RequestBody PayableService.PaymentInput input,HttpSession session) {return payables.pay(actor(session),id,input);}
    @GetMapping("/journals") public List<JournalService.JournalView> journals(HttpSession session) {return journals.list(actor(session).storeId());}
    @GetMapping("/trial-balance") public List<JournalService.BalanceView> balance(HttpSession session) {return journals.trialBalance(actor(session).storeId());}
}
