package com.kongklang.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SessionModelAdvice {

    @ModelAttribute("sessionUserId")
    public Object sessionUserId(HttpSession session) {
        return session.getAttribute("userId");
    }

    @ModelAttribute("sessionUsername")
    public Object sessionUsername(HttpSession session) {
        return session.getAttribute("username");
    }

    @ModelAttribute("sessionDisplayName")
    public Object sessionDisplayName(HttpSession session) {
        return session.getAttribute("displayName");
    }

    @ModelAttribute("sessionRole")
    public Object sessionRole(HttpSession session) {
        return session.getAttribute("role");
    }

    @ModelAttribute("sessionStoreId")
    public Object sessionStoreId(HttpSession session) {
        return session.getAttribute("storeId");
    }

    @ModelAttribute("sessionStoreCode")
    public Object sessionStoreCode(HttpSession session) {
        return session.getAttribute("storeCode");
    }

    @ModelAttribute("sessionStoreName")
    public Object sessionStoreName(HttpSession session) {
        return session.getAttribute("storeName");
    }
}