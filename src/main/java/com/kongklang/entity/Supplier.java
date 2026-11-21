package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="suppliers", uniqueConstraints=@UniqueConstraint(name="uk_supplier_store_code",columnNames={"store_id","code"}))
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;
    public Store getStore() { return store; }
    public void setStore(Store value) { this.store = value; }
    @Column(nullable=false)
    private String code;
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    @Column(nullable=false)
    private String name;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    @Column
    private String contactName;
    public String getContactName() { return contactName; }
    public void setContactName(String value) { this.contactName = value; }
    @Column
    private String phone;
    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value; }
    @Column
    private String email;
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value; }
    @Column(length=1000)
    private String address;
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value; }
    @Column(nullable=false)
    private Boolean active = true;
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { this.active = value; }
}
