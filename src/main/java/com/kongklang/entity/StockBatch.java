
package com.kongklang.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import com.kongklang.support.DecimalMoney;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_batches")
public class StockBatch {
    @Column(nullable=false,precision=19,scale=2) private BigDecimal receivedValue;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal remainingValue;
    public BigDecimal getReceivedValue(){return receivedValue;}
    public void setReceivedValue(BigDecimal value){receivedValue=DecimalMoney.normalize(value,2);}
    public BigDecimal getRemainingValue(){return remainingValue;}
    public void setRemainingValue(BigDecimal value){remainingValue=DecimalMoney.normalize(value,2);}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer remainingQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal purchaseCost;

    private LocalDate expiryDate;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(length=1200)
    private String note;

    // ร้านเจ้าของล็อตสินค้า
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    public StockBatch() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(Integer remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public Double getPurchaseCost() { return purchaseCost == null ? null : purchaseCost.doubleValue(); }

    public void setPurchaseCost(Double purchaseCost) { this.purchaseCost = DecimalMoney.legacy(purchaseCost, 4); }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getPurchaseCostValue() { return purchaseCost; }
    public void setPurchaseCostValue(BigDecimal value) { this.purchaseCost = DecimalMoney.normalize(value, 4); }
}
