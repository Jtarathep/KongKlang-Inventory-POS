package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Controller
public class AuditController {
    private final AccessService access;private final AuditTrail audit;
    public AuditController(AccessService access,AuditTrail audit){this.access=access;this.audit=audit;}
    @GetMapping("/reports/audit") public String page(HttpSession session,Model model){model.addAttribute("events",api(session));return "audit-log";}
    @GetMapping("/api/reports/audit") @ResponseBody public List<AuditTrail.Event> api(HttpSession session){return audit.recent(access.require(session,"OWNER","ADMIN","ACCOUNTING").storeId());}
}
