package com.kongklang.service;

import com.kongklang.entity.UserAccount;
import com.kongklang.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {

        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount login(String username, String password) {
        if(username==null||username.length()>80||password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)return null;

        UserAccount user = userAccountRepository
                .findByUsernameAndActiveTrue(username)
                .orElse(null);

        if (user == null || user.getStore() == null || !Boolean.TRUE.equals(user.getStore().getActive())) {
            return null;
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }

        return user;
    }

    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }
}
