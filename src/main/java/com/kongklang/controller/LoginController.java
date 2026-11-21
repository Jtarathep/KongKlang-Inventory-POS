package com.kongklang.controller;

import com.kongklang.entity.UserAccount;
import com.kongklang.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class LoginController {

    private final AuthService authService;
    private final com.kongklang.service.LoginThrottle throttle;

    public LoginController(AuthService authService,com.kongklang.service.LoginThrottle throttle) {
        this.authService = authService;
        this.throttle=throttle;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if(session.getAttribute("csrfToken")==null) session.setAttribute("csrfToken", java.util.UUID.randomUUID().toString());

        if (session.getAttribute("userId") != null) {
            return "SYSTEM_ADMIN".equals(session.getAttribute("role")) ? "redirect:/management/stores" : "redirect:/";
        }

        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request,
            Model model) {

        username = username.trim();

        if (username.isEmpty() || password.isEmpty()) {
            model.addAttribute(
                    "error",
                    "กรุณากรอกชื่อผู้ใช้และรหัสผ่าน"
            );

            model.addAttribute("username", username);

            return "login";
        }

        if(username.length()>80||!throttle.allow(request.getRemoteAddr(),username)){
            model.addAttribute("error","ยังเข้าสู่ระบบไม่ได้ กรุณาตรวจข้อมูลและลองใหม่ภายหลัง");return "login";
        }
        UserAccount user =
                authService.login(username, password);

        if (user == null) {
            model.addAttribute(
                    "error",
                    "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง"
            );

            model.addAttribute("username", username);

            return "login";
        }

        // เปลี่ยน Session ID หลัง Login เพื่อป้องกัน Session Fixation
        throttle.success(username);
        request.changeSessionId();
        session.setAttribute("csrfToken", java.util.UUID.randomUUID().toString());

        // เก็บข้อมูล User ลง Session
        session.setAttribute("userId", user.getId());
        session.setAttribute("credentialVersion",user.getCredentialVersion());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("displayName", user.getDisplayName());
        session.setAttribute("role", user.getRole());

        // เก็บข้อมูล Store ลง Session
        if (user.getStore() != null) {

            session.setAttribute(
                    "storeId",
                    user.getStore().getId()
            );

            session.setAttribute(
                    "storeCode",
                    user.getStore().getStoreCode()
            );

            session.setAttribute(
                    "storeName",
                    user.getStore().getStoreName()
            );
        }

        return "SYSTEM_ADMIN".equals(user.getRole()) ? "redirect:/management/stores" : "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}
