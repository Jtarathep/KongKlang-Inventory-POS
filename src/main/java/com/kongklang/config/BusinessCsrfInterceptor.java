package com.kongklang.config;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class BusinessCsrfInterceptor implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        if(Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) return true;
        HttpSession session=request.getSession(false);
        String token=session==null?null:(String)session.getAttribute("csrfToken");
        String supplied=request.getHeader("X-CSRF-Token");
        if(supplied==null) supplied=request.getParameter("_csrf");
        if(token!=null&&supplied!=null&&MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) return true;
        response.setStatus(403);response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"กรุณาเปิดหน้าที่กำลังใช้งานใหม่เพื่อรับรหัสยืนยันก่อนทำรายการ\"}");
        return false;
    }
}
