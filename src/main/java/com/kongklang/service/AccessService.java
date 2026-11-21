package com.kongklang.service;
import com.kongklang.entity.UserAccount;
import com.kongklang.repository.UserAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@Service
public class AccessService {
    private final UserAccountRepository users;
    public AccessService(UserAccountRepository users) { this.users=users; }
    @Transactional(readOnly=true)
    public Actor require(HttpSession session,String... allowedRoles) {
        Object id=session==null?null:session.getAttribute("userId");
        if (!(id instanceof Number)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"กรุณาเข้าสู่ระบบ");
        UserAccount user=users.findById(((Number)id).longValue()).orElseThrow(
                ()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"ไม่พบบัญชีผู้ใช้"));
        if (!Boolean.TRUE.equals(user.getActive()) || user.getStore()==null || !Boolean.TRUE.equals(user.getStore().getActive()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"บัญชีหรือร้านถูกปิดใช้งาน");
        Object credential=session.getAttribute("credentialVersion");
        if((credential instanceof Number number?number.longValue():0L)!=user.getCredentialVersion())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"รหัสผ่านเปลี่ยนแล้ว กรุณาเข้าสู่ระบบใหม่");
        if (allowedRoles.length>0 && !Set.of(allowedRoles).contains(user.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ไม่มีสิทธิ์ทำรายการนี้");
        session.setAttribute("role",user.getRole());
        session.setAttribute("passwordChangeRequired",user.isPasswordChangeRequired());
        session.setAttribute("storeId",user.getStore().getId());
        if(session.getAttribute("csrfToken")==null) session.setAttribute("csrfToken",java.util.UUID.randomUUID().toString());
        return new Actor(user.getId(),user.getStore().getId(),user.getRole());
    }
    public record Actor(Long userId,Long storeId,String role) {}
}
