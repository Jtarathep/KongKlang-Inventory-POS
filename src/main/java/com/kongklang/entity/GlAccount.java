package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="gl_accounts", uniqueConstraints=@UniqueConstraint(name="uk_gl_account_store_code",columnNames={"store_id","code"}))
public class GlAccount {
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
    @Column(nullable=false)
    private String type;
    public String getType() { return type; }
    public void setType(String value) { this.type = value; }
}
