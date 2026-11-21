package com.kongklang.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
public class Store {
    @Column(length=20,nullable=false) private String taxMode="NONE";
    @Column(precision=5,scale=2,nullable=false) private java.math.BigDecimal vatRate=new java.math.BigDecimal("7.00");
    @Column(nullable=false) private boolean vatRegistered;
    @Column(nullable=false) private long taxRevision;
    @Column(length=255) private String legalName;
    @Column(length=1000) private String legalAddress;
    @Column(length=13) private String taxId;
    @Column(length=5) private String branchCode;
    public String getTaxMode(){return taxMode;} public void setTaxMode(String v){taxMode=v;}
    public java.math.BigDecimal getVatRate(){return vatRate;} public void setVatRate(java.math.BigDecimal v){vatRate=v;}
    public boolean isVatRegistered(){return vatRegistered;} public void setVatRegistered(boolean v){vatRegistered=v;}
    public long getTaxRevision(){return taxRevision;} public void setTaxRevision(long v){taxRevision=v;}
    public String getLegalName(){return legalName;} public void setLegalName(String v){legalName=v;}
    public String getLegalAddress(){return legalAddress;} public void setLegalAddress(String v){legalAddress=v;}
    public String getTaxId(){return taxId;} public void setTaxId(String v){taxId=v;}
    public String getBranchCode(){return branchCode;} public void setBranchCode(String v){branchCode=v;}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // รหัสร้าน
    @Column(nullable = false, unique = true)
    private String storeCode;

    // ชื่อร้าน
    @Column(nullable = false)
    private String storeName;

    // ที่อยู่ร้าน
    private String address;

    // เบอร์โทรศัพท์
    private String phone;

    // สถานะร้าน
    @Column(nullable = false)
    private Boolean active = true;

    // วันที่สร้างร้าน
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Store() {
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

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
