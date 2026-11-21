package com.kongklang.controller;

import com.kongklang.entity.Store;
import com.kongklang.entity.UserAccount;
import com.kongklang.repository.StoreRepository;
import com.kongklang.repository.UserAccountRepository;
import com.kongklang.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@org.springframework.transaction.annotation.Transactional
@RequestMapping("/management")
public class ManagementController {

    private final StoreRepository storeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;
    private final com.kongklang.service.AuditTrail auditTrail;

    public ManagementController(StoreRepository storeRepository,
                                UserAccountRepository userAccountRepository,
                                AuthService authService, com.kongklang.service.AuditTrail auditTrail) {
        this.auditTrail=auditTrail;
        this.storeRepository = storeRepository;
        this.userAccountRepository = userAccountRepository;
        this.authService = authService;
    }

    private void audit(HttpSession session,String action,String reference) {
        auditTrail.record(new com.kongklang.service.AccessService.Actor(((Number)session.getAttribute("userId")).longValue(),((Number)session.getAttribute("storeId")).longValue(),String.valueOf(session.getAttribute("role"))),action,reference);
    }

    private boolean isOwner(HttpSession session) {
        return session != null && "SYSTEM_ADMIN".equals(session.getAttribute("role"));
    }

    private boolean isManager(HttpSession session) {
        return session != null && java.util.Set.of("SYSTEM_ADMIN", "OWNER", "ADMIN").contains(String.valueOf(session.getAttribute("role")));
    }
    private Long shop(HttpSession session) {return ((Number)session.getAttribute("storeId")).longValue();}

    @GetMapping("/stores")
    public String stores(HttpSession session, Model model) {
        if (!isOwner(session)) return "redirect:/";
        model.addAttribute("stores", storeRepository.findAllByOrderByCreatedAtDesc());
        return "management-stores";
    }

    @PostMapping("/stores/create")
    public String createStore(@RequestParam String storeCode,
                              @RequestParam String storeName,
                              @RequestParam(required = false) String address,
                              @RequestParam(required = false) String phone,
                              HttpSession session,
                              RedirectAttributes redirect) {
        if (!isOwner(session)) return "redirect:/";
        String code = storeCode == null ? "" : storeCode.trim().toUpperCase();
        String name = storeName == null ? "" : storeName.trim();
        if (code.isBlank() || name.isBlank() || code.length()>80 || name.length()>255 || (address!=null&&address.length()>255) || (phone!=null&&phone.length()>80)) {
            redirect.addFlashAttribute("error", "กรุณากรอกรหัสร้านและชื่อร้าน");
        } else if (storeRepository.existsByStoreCode(code)) {
            redirect.addFlashAttribute("error", "รหัสร้านนี้มีอยู่แล้ว");
        } else {
            Store store = new Store();
            store.setStoreCode(code);
            store.setStoreName(name);
            store.setAddress(address == null ? "" : address.trim());
            store.setPhone(phone == null ? "" : phone.trim());
            store.setActive(true);
            storeRepository.save(store);
            audit(session,"STORE_CREATE",code);
            redirect.addFlashAttribute("success", "เพิ่มร้าน " + code + " เรียบร้อยแล้ว");
        }
        return "redirect:/management/stores";
    }

    @PostMapping("/stores/{id}/toggle")
    public String toggleStore(@PathVariable Long id, HttpSession session,
                              RedirectAttributes redirect) {
        if (!isOwner(session)) return "redirect:/";
        storeRepository.findById(id).ifPresent(store -> {
            if (store.getId().equals(session.getAttribute("storeId"))) {
                redirect.addFlashAttribute("error", "ไม่สามารถปิดร้านที่กำลังใช้งานอยู่ได้");
            } else {
                store.setActive(!Boolean.TRUE.equals(store.getActive()));
                storeRepository.save(store);
                audit(session,"STORE_STATUS",store.getStoreCode());
                redirect.addFlashAttribute("success", "ปรับสถานะร้านเรียบร้อยแล้ว");
            }
        });
        return "redirect:/management/stores";
    }

    @GetMapping("/users")
    public String users(HttpSession session, Model model) {
        if (!isManager(session)) return "redirect:/";
        model.addAttribute("users", (isOwner(session) ? userAccountRepository.findAllByOrderByCreatedAtDesc() : userAccountRepository.findAllByStoreIdOrderByCreatedAtDesc(shop(session))));
        model.addAttribute("systemAdmin", isOwner(session));
        model.addAttribute("stores", isOwner(session) ? storeRepository.findAllByOrderByCreatedAtDesc() : java.util.List.of(storeRepository.findById(shop(session)).orElseThrow()));
        return "management-users";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String displayName,
                             @RequestParam String role,
                             @RequestParam Long storeId,
                             HttpSession session,
                             RedirectAttributes redirect) {
        if (!isManager(session)) return "redirect:/";
        if (!isOwner(session) && !shop(session).equals(storeId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        if (!isOwner(session) && ("OWNER".equals(role) || ("ADMIN".equals(role) && !"OWNER".equals(session.getAttribute("role")))))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        String cleanUsername = username == null ? "" : username.trim();
        String cleanName = displayName == null ? "" : displayName.trim();
        if (cleanUsername.isBlank() || cleanName.isBlank() || password == null || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || cleanUsername.length() > 80 || cleanName.length() > 255) {
            redirect.addFlashAttribute("error", "กรอกข้อมูลให้ครบ และรหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร");
        } else if (userAccountRepository.existsByUsername(cleanUsername)) {
            redirect.addFlashAttribute("error", "ชื่อผู้ใช้นี้ถูกใช้แล้ว");
        } else if (!java.util.Set.of("OWNER", "ADMIN", "STAFF", "SALES", "WAREHOUSE", "PURCHASING", "ACCOUNTING").contains(role)) {
            redirect.addFlashAttribute("error", "Role ที่เลือกไม่ถูกต้อง");
        } else {
            Store store = storeRepository.findById(storeId).orElse(null);
            if (store == null || !Boolean.TRUE.equals(store.getActive())) {
                redirect.addFlashAttribute("error", "ไม่พบร้าน หรือร้านถูกปิดใช้งาน");
            } else {
                UserAccount user = new UserAccount();
                user.setUsername(cleanUsername);
                user.setPassword(authService.encodePassword(password));
                user.setDisplayName(cleanName);
                user.setRole(role);
                user.setStore(store);
                user.setActive(true);
                userAccountRepository.save(user);
                audit(session,"USER_CREATE",user.getUsername());
                redirect.addFlashAttribute("success", "สร้างบัญชีผู้ใช้เรียบร้อยแล้ว");
            }
        }
        return "redirect:/management/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, HttpSession session,
                             RedirectAttributes redirect) {
        if (!isManager(session)) return "redirect:/";
        (isOwner(session) ? userAccountRepository.findById(id) : userAccountRepository.findByIdAndStoreId(id, shop(session))).ifPresent(user -> {
            if ("SYSTEM_ADMIN".equals(user.getRole()) || (!isOwner(session) && "OWNER".equals(user.getRole())) || ("ADMIN".equals(session.getAttribute("role")) && "ADMIN".equals(user.getRole()))) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            }
            if (user.getId().equals(session.getAttribute("userId"))) {
                redirect.addFlashAttribute("error", "ไม่สามารถปิดบัญชีที่กำลัง Login อยู่ได้");
            } else {
                user.setActive(!Boolean.TRUE.equals(user.getActive()));
                userAccountRepository.save(user);
                audit(session,"USER_STATUS",user.getUsername());
                redirect.addFlashAttribute("success", "ปรับสถานะบัญชีเรียบร้อยแล้ว");
            }
        });
        return "redirect:/management/users";
    }
}
