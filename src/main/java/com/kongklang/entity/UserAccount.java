package com.kongklang.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "app_users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_app_users_username",
                        columnNames = "username"
                )
        }
)
public class UserAccount {
    @Column(nullable=false) private long credentialVersion;
    @Column(nullable=false) private boolean passwordChangeRequired;
    public long getCredentialVersion(){return credentialVersion;} public void setCredentialVersion(long v){credentialVersion=v;}
    public boolean isPasswordChangeRequired(){return passwordChangeRequired;} public void setPasswordChangeRequired(boolean v){passwordChangeRequired=v;}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ชื่อผู้ใช้สำหรับ Login
    @Column(nullable = false)
    private String username;

    // รหัสผ่าน
    // ตอนนี้เก็บเป็น String เพื่อรองรับ Password Hash ในขั้น Login
    @Column(nullable = false)
    private String password;

    // ชื่อผู้ใช้งาน
    @Column(nullable = false)
    private String displayName;

    // OWNER / ADMIN / STAFF
    @Column(nullable = false)
    private String role;

    // ร้านที่ User สังกัด
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // เปิด/ปิดบัญชี
    @Column(nullable = false)
    private Boolean active = true;

    // วันที่สร้าง User
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public UserAccount() {
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bangkok"));
        }

        if (active == null) {
            active = true;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
