package com.kongklang.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import com.kongklang.support.DecimalMoney;

@Entity
@Table(name = "sale_items")
public class SaleItem {
    @Column(precision=19,scale=2) private BigDecimal listedAmount;
    @Column(precision=19,scale=2) private BigDecimal discountAmount;
    @Column(precision=19,scale=2) private BigDecimal netAmount;
    @Column(precision=19,scale=2) private BigDecimal taxAmount;
    public BigDecimal getListedAmount(){return listedAmount==null?subtotal:listedAmount;}
    public void setListedAmount(BigDecimal v){listedAmount=v;}
    public BigDecimal getDiscountAmount(){return discountAmount==null?BigDecimal.ZERO:discountAmount;}
    public void setDiscountAmount(BigDecimal v){discountAmount=v;}
    public BigDecimal getNetAmount(){return netAmount==null?subtotal:netAmount;}
    public void setNetAmount(BigDecimal v){netAmount=v;}
    public BigDecimal getTaxAmount(){return taxAmount==null?BigDecimal.ZERO:taxAmount;}
    public void setTaxAmount(BigDecimal v){taxAmount=v;}
    @Column(precision=19,scale=4) private BigDecimal actualCost;
    public BigDecimal getActualCost(){return actualCost;}
    public void setActualCost(BigDecimal value){actualCost=DecimalMoney.normalize(value,4);}


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // บิลที่สินค้าอยู่ในนั้น
    @ManyToOne
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    // รหัสสินค้า
    @Column(nullable = false)
    private String productCode;

    // ชื่อสินค้า ณ ตอนขาย
    @Column(nullable = false)
    private String productName;

    // จำนวนที่ขาย
    @Column(nullable = false)
    private Integer quantity;

    // ราคาขายต่อหน่วย ณ ตอนขาย
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    // ต้นทุนสินค้าที่ขาย
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cost;

    // ราคารวม
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    public SaleItem() {
    }

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
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

    public Double getPrice() { return price == null ? null : price.doubleValue(); }

    public void setPrice(Double price) { this.price = DecimalMoney.legacy(price, 2); }

    public Double getCost() { return cost == null ? null : cost.doubleValue(); }

    public void setCost(Double cost) { this.cost = DecimalMoney.legacy(cost, 2); }

    public Double getSubtotal() { return subtotal == null ? null : subtotal.doubleValue(); }

    public void setSubtotal(Double subtotal) { this.subtotal = DecimalMoney.legacy(subtotal, 2); }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getPriceValue() { return price; }
    public void setPriceValue(BigDecimal value) { this.price = DecimalMoney.normalize(value, 2); }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getCostValue() { return cost; }
    public void setCostValue(BigDecimal value) { this.cost = DecimalMoney.normalize(value, 2); }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getSubtotalValue() { return subtotal; }
    public void setSubtotalValue(BigDecimal value) { this.subtotal = DecimalMoney.normalize(value, 2); }
}
