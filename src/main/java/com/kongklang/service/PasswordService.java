package com.kongklang.service;
import com.kongklang.entity.UserAccount;
import com.kongklang.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
@Transactional
public class PasswordService {
    private final UserAccountRepository users;private final PasswordEncoder encoder;private final AuditTrail audit;
    public PasswordService(UserAccountRepository users,PasswordEncoder encoder,AuditTrail audit){this.users=users;this.encoder=encoder;this.audit=audit;}
    public void change(AccessService.Actor actor,String current,String next){
        validate(next);UserAccount user=users.lockById(actor.userId()).orElseThrow(()->new IllegalArgumentException("ไม่พบบัญชี"));
        if(current==null||current.getBytes(StandardCharsets.UTF_8).length>72||!encoder.matches(current,user.getPassword()))throw new IllegalArgumentException("รหัสผ่านปัจจุบันไม่ถูกต้อง");
        if(encoder.matches(next,user.getPassword()))throw new IllegalArgumentException("รหัสใหม่ต้องต่างจากรหัสเดิม");
        save(user,next,false);audit.record(actor,"PASSWORD_CHANGE",user.getUsername());
    }
    public void reset(AccessService.Actor actor,Long id,String temporary){
        if(!java.util.Set.of("OWNER","ADMIN","SYSTEM_ADMIN").contains(actor.role())||actor.userId().equals(id))throw new IllegalArgumentException("ไม่มีสิทธิ์ตั้งรหัสชั่วคราวให้บัญชีนี้");
        validate(temporary);UserAccount target=users.lockById(id).orElseThrow(()->new IllegalArgumentException("ไม่พบบัญชีที่จัดการได้"));
        boolean central=actor.role().equals("SYSTEM_ADMIN");
        if(target.getRole().equals("SYSTEM_ADMIN")||!central&&(!target.getStore().getId().equals(actor.storeId())||target.getRole().equals("OWNER")||actor.role().equals("ADMIN")&&target.getRole().equals("ADMIN")))throw new IllegalArgumentException("ไม่พบบัญชีที่จัดการได้");
        save(target,temporary,true);audit.record(actor,"PASSWORD_RESET",target.getUsername());
    }
    private void save(UserAccount user,String password,boolean temporary){user.setPassword(encoder.encode(password));user.setCredentialVersion(Math.addExact(user.getCredentialVersion(),1));user.setPasswordChangeRequired(temporary);users.save(user);}
    private void validate(String value){if(value==null||value.isBlank()||value.codePointCount(0,value.length())<12||value.getBytes(StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("ใช้รหัสผ่านอย่างน้อย 12 ตัวอักษร และไม่เกิน 72 ไบต์");}
}
