package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordController {
    private final AccessService access;private final PasswordService passwords;
    public PasswordController(AccessService access,PasswordService passwords){this.access=access;this.passwords=passwords;}
    @GetMapping("/settings/password") public String page(HttpSession s){access.require(s);return "password-settings";}
    @PostMapping("/settings/password") public String change(@RequestParam String current,@RequestParam String next,@RequestParam String confirm,HttpSession s,Model model){
        var actor=access.require(s);
        try{if(!next.equals(confirm))throw new IllegalArgumentException("รหัสใหม่และช่องยืนยันไม่ตรงกัน");passwords.change(actor,current,next);s.invalidate();return "redirect:/login?passwordChanged";}
        catch(IllegalArgumentException e){model.addAttribute("error",e.getMessage());return "password-settings";}
    }
    @PostMapping("/management/users/{id}/password") public String reset(@PathVariable Long id,@RequestParam String temporary,HttpSession s,RedirectAttributes redirect){
        var actor=access.require(s,"SYSTEM_ADMIN","OWNER","ADMIN");
        try{passwords.reset(actor,id,temporary);redirect.addFlashAttribute("success","ตั้งรหัสผ่านชั่วคราวแล้ว ผู้ใช้ต้องเปลี่ยนรหัสเมื่อเข้าสู่ระบบครั้งถัดไป");}
        catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());}
        return "redirect:/management/users";
    }
}
