package com.kongklang.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import com.kongklang.support.DecimalMoney;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_products_store_code",
                        columnNames = {"store_id", "code"}
                ),
                @UniqueConstraint(
                        name = "uk_products_store_barcode",
                        columnNames = {"store_id", "barcode"}
                )
        }
)
public class Product {
    @Version @Column(nullable=false)
    private Long version;
    public Long getVersion(){return version;}
    public void setVersion(Long value){version=value;}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // รหัสสินค้า
    @Column(nullable = false)
    private String code;

    @Column(length=2000)
    private String description;
    @Column(nullable=false)
    private Boolean active=true;
    public String getDescription(){return description;}
    public void setDescription(String value){description=value;}
    public Boolean getActive(){return active;}
    public void setActive(Boolean value){active=value;}

    // Barcode
    @Column
    private String barcode;

    // ชื่อสินค้า
    @Column(nullable = false)
    private String name;

    // จำนวนคงเหลือ
    private Integer quantity;

    // ราคาขาย
    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    // หมวดหมู่
    private String category;

    // หน่วยนับ
    private String unit;

    // ต้นทุนล่าสุดต่อหน่วย
    @Column(precision = 19, scale = 4)
    private BigDecimal costPrice;

    // ร้านค้าที่เป็นเจ้าของสินค้า
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;


    // Constructor
    public Product() {
    }


    // ==============================
    // Getter / Setter
    // ==============================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }


    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }


    public Double getPrice() { return price == null ? null : price.doubleValue(); }

    public void setPrice(Double price) { this.price = DecimalMoney.legacy(price, 2); }


    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }


    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }


    public Double getCostPrice() { return costPrice == null ? null : costPrice.doubleValue(); }

    public void setCostPrice(Double costPrice) { this.costPrice = DecimalMoney.legacy(costPrice, 4); }


    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getPriceValue() { return price; }
    public void setPriceValue(BigDecimal value) { this.price = DecimalMoney.normalize(value, 2); }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getCostPriceValue() { return costPrice; }
    public void setCostPriceValue(BigDecimal value) { this.costPrice = DecimalMoney.normalize(value, 4); }
}
