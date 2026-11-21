package com.kongklang.config;
import com.kongklang.service.AccessService;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    private final AccessService access;
    public LoginInterceptor(AccessService access) {this.access=access;}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        String uri=request.getServletPath();
        if(uri==null||uri.isEmpty())uri=request.getRequestURI().substring(request.getContextPath().length());
        if(uri.equals("/login")||uri.equals("/error")||uri.startsWith("/css/")||uri.startsWith("/js/")||uri.startsWith("/images/")||uri.startsWith("/favicon")) return true;
        try {
            AccessService.Actor actor=access.require(request.getSession(false));
            if(Boolean.TRUE.equals(request.getSession(false).getAttribute("passwordChangeRequired"))&&!uri.equals("/settings/password")&&!uri.equals("/logout")){
                if(uri.startsWith("/api/")){response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"กรุณาเปลี่ยนรหัสผ่านชั่วคราวก่อนใช้งาน\"}");}
                else response.sendRedirect(request.getContextPath()+"/settings/password");return false;
            }
            if(actor.role().equals("SYSTEM_ADMIN")&&uri.equals("/")){response.sendRedirect(request.getContextPath()+"/management/stores");return false;}
            if(!com.kongklang.service.RolePolicy.allows(actor.role(),request.getMethod(),uri)) {
                if(uri.startsWith("/api/")) {
                    response.setStatus(403);response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"message\":\"บทบาทของคุณไม่มีสิทธิ์ทำรายการนี้\"}");
                } else response.sendError(403,"ไม่มีสิทธิ์เข้าถึงส่วนงานนี้");
                return false;
            }
            return true;
        } catch(ResponseStatusException error) {
            HttpSession session=request.getSession(false); if(session!=null) session.invalidate();
            if(uri.startsWith("/api/")) {
                response.setStatus(401);response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"กรุณาเข้าสู่ระบบด้วยบัญชีและร้านที่เปิดใช้งาน\"}");
            } else response.sendRedirect(request.getContextPath()+"/login");
            return false;
        }
    }
}
