package com.kongklang.controller;
import com.kongklang.service.AccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BusinessPageController {
    private final AccessService access;
    public BusinessPageController(AccessService access){this.access=access;}
    @GetMapping("/purchasing") public String purchasing(HttpSession session) {
        access.require(session,"OWNER","ADMIN","PURCHASING","WAREHOUSE","ACCOUNTING");return "purchasing";
    }
    @GetMapping("/accounting") public String accounting(HttpSession session) {
        access.require(session,"OWNER","ADMIN","ACCOUNTING");return "accounting";
    }
}
