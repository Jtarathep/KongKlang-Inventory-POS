package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="purchase_order_items")
public class PurchaseOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purchaseOrder_id",nullable=false)
    private PurchaseOrder purchaseOrder;
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder value) { this.purchaseOrder = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id",nullable=false)
    private Product product;
    public Product getProduct() { return product; }
    public void setProduct(Product value) { this.product = value; }
    @Column(nullable=false)
    private String productCode;
    public String getProductCode() { return productCode; }
    public void setProductCode(String value) { this.productCode = value; }
    @Column(nullable=false)
    private String productName;
    public String getProductName() { return productName; }
    public void setProductName(String value) { this.productName = value; }
    @Column(nullable=false)
    private Integer quantity;
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    @Column(nullable=false)
    private Integer receivedQuantity = 0;
    public Integer getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(Integer value) { this.receivedQuantity = value; }
    @Column(nullable=false,precision=19,scale=4)
    private BigDecimal unitCost;
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
}
