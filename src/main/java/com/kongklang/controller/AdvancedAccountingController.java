package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import java.time.*;

@Controller
public class AdvancedAccountingController {
    private final AccessService access;private final AdvancedAccountingService reports;private final AccountingPeriodService periods;
    public AdvancedAccountingController(AccessService access,AdvancedAccountingService reports,AccountingPeriodService periods){this.access=access;this.reports=reports;this.periods=periods;}
    private AccessService.Actor actor(HttpSession s){return access.require(s,"OWNER","ADMIN","ACCOUNTING");}
    @GetMapping("/accounting/advanced") public String page(@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,@RequestParam(defaultValue="1100") String account,HttpSession session,Model model){
        var a=actor(session);LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));if(start==null)start=today.withDayOfMonth(1);if(end==null)end=today;
        var list=periods.list(a.storeId());YearMonth month=YearMonth.from(end);var current=list.stream().filter(p->p.month().equals(month.atDay(1))).findFirst();
        model.addAttribute("start",start);model.addAttribute("end",end);model.addAttribute("account",account);model.addAttribute("income",reports.income(a.storeId(),start,end));model.addAttribute("balance",reports.balanceSheet(a.storeId(),end));model.addAttribute("aging",reports.aging(a.storeId(),end));model.addAttribute("ledger",reports.ledger(a.storeId(),account,start,end));model.addAttribute("periods",list);model.addAttribute("closeMonth",month.toString());model.addAttribute("periodRevision",current.map(AccountingPeriodService.View::revision).orElse(0L));return "accounting-advanced";
    }
    @PostMapping("/accounting/periods/{action}") public String period(@PathVariable String action,@RequestParam String month,@RequestParam long revision,@RequestParam String reason,@RequestParam(defaultValue="false") boolean confirmed,HttpSession session,RedirectAttributes redirect){
        var a=access.require(session,action.equals("reopen")?new String[]{"OWNER"}:new String[]{"OWNER","ADMIN"});var input=new AccountingPeriodService.Input(month,revision,reason,confirmed);
        try{if(action.equals("close"))periods.close(a,input);else if(action.equals("reopen"))periods.reopen(a,input);else throw new IllegalArgumentException("ไม่พบคำสั่งงวดบัญชี");redirect.addFlashAttribute("success",action.equals("close")?"ปิดงวดแล้ว เก็บงบทดลองและรายการโอนกำไรสะสมเรียบร้อย":"เปิดงวดแล้ว สร้างรายการกลับบัญชีโดยเก็บประวัติเดิมไว้");}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());}return "redirect:/accounting/advanced";
    }
    @GetMapping("/api/accounting/income") @ResponseBody public AdvancedAccountingService.Income income(@RequestParam LocalDate start,@RequestParam LocalDate end,HttpSession s){return reports.income(actor(s).storeId(),start,end);}
    @GetMapping("/api/accounting/balance-sheet") @ResponseBody public AdvancedAccountingService.BalanceSheet balance(@RequestParam LocalDate asOf,HttpSession s){return reports.balanceSheet(actor(s).storeId(),asOf);}
    @GetMapping("/api/accounting/aging") @ResponseBody public Object aging(@RequestParam LocalDate asOf,HttpSession s){return reports.aging(actor(s).storeId(),asOf);}
    @GetMapping("/api/accounting/ledger") @ResponseBody public AdvancedAccountingService.Ledger ledger(@RequestParam String account,@RequestParam LocalDate start,@RequestParam LocalDate end,HttpSession s){return reports.ledger(actor(s).storeId(),account,start,end);}
    @GetMapping("/api/accounting/periods") @ResponseBody public Object periods(HttpSession s){return periods.list(actor(s).storeId());}
    @PostMapping("/api/accounting/periods/{action}") @ResponseBody public ResponseEntity<?> periodApi(@PathVariable String action,@RequestBody AccountingPeriodService.Input input,HttpSession s){var a=access.require(s,action.equals("reopen")?new String[]{"OWNER"}:new String[]{"OWNER","ADMIN"});try{if(!action.equals("close")&&!action.equals("reopen"))throw new IllegalArgumentException("คำสั่งไม่ถูกต้อง");return ResponseEntity.ok(action.equals("close")?periods.close(a,input):periods.reopen(a,input));}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(java.util.Map.of("message",e.getMessage()));}}
}
