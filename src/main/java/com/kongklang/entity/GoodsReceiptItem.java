package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="goods_receipt_items")
public class GoodsReceiptItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="receipt_id",nullable=false)
    private GoodsReceipt receipt;
    public GoodsReceipt getReceipt() { return receipt; }
    public void setReceipt(GoodsReceipt value) { this.receipt = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="orderItem_id",nullable=false)
    private PurchaseOrderItem orderItem;
    public PurchaseOrderItem getOrderItem() { return orderItem; }
    public void setOrderItem(PurchaseOrderItem value) { this.orderItem = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="stockBatch_id",nullable=false)
    private StockBatch stockBatch;
    public StockBatch getStockBatch() { return stockBatch; }
    public void setStockBatch(StockBatch value) { this.stockBatch = value; }
    @Column(nullable=false)
    private Integer quantity;
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    @Column(nullable=false,precision=19,scale=4)
    private BigDecimal unitCost;
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
    @Column
    private LocalDate expiryDate;
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate value) { this.expiryDate = value; }
}
