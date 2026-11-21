package com.kongklang.config;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit provisioning; never promotes an existing shop owner. */
@Component
@ConditionalOnProperty(name="app.system-admin.bootstrap",havingValue="true")
public class SystemAdminInitializer implements CommandLineRunner {
    private final StoreRepository stores;private final UserAccountRepository users;private final PasswordEncoder encoder;
    @Value("${KONGKLANG_SYSTEM_USERNAME:}") private String username;
    @Value("${KONGKLANG_SYSTEM_PASSWORD:}") private String password;
    public SystemAdminInitializer(StoreRepository stores,UserAccountRepository users,PasswordEncoder encoder){this.stores=stores;this.users=users;this.encoder=encoder;}
    @Override @Transactional public void run(String... args) {
        if(username.isBlank()||password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Set a system username and password of at least 12 characters and at most 72 UTF-8 bytes");
        var existing=users.findByUsername(username.trim());
        if(existing.isPresent()) {
            if(!existing.get().getRole().equals("SYSTEM_ADMIN"))throw new IllegalStateException("Username belongs to a shop account");
            return;
        }
        Store store=stores.findByStoreCode("__SYSTEM__").orElseGet(()->{
            Store s=new Store();s.setStoreCode("__SYSTEM__");s.setStoreName("ระบบกลาง KONGKLANG");s.setActive(true);return stores.save(s);
        });
        UserAccount user=new UserAccount();user.setUsername(username.trim());user.setPassword(encoder.encode(password));
        user.setDisplayName("ผู้ดูแลระบบกลาง");user.setRole("SYSTEM_ADMIN");user.setStore(store);user.setActive(true);users.save(user);
    }
}
