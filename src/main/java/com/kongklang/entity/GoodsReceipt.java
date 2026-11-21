package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="goods_receipts", uniqueConstraints=@UniqueConstraint(name="uk_receipt_store_key",columnNames={"store_id","request_key"}))
public class GoodsReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;
    public Store getStore() { return store; }
    public void setStore(Store value) { this.store = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purchaseOrder_id",nullable=false)
    private PurchaseOrder purchaseOrder;
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder value) { this.purchaseOrder = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="receivedBy_id",nullable=false)
    private UserAccount receivedBy;
    public UserAccount getReceivedBy() { return receivedBy; }
    public void setReceivedBy(UserAccount value) { this.receivedBy = value; }
    @Column(nullable=false,unique=true)
    private String number;
    public String getNumber() { return number; }
    public void setNumber(String value) { this.number = value; }
    @Column(nullable=false,length=100)
    private String requestKey;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    @Column(nullable=false,length=64)
    private String requestFingerprint;
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { this.requestFingerprint = value; }
    @Column(nullable=false)
    private LocalDateTime receivedAt;
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { this.receivedAt = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal total;
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { this.total = value; }
    @OneToMany(mappedBy="receipt", cascade=CascadeType.ALL)
    private List<GoodsReceiptItem> items = new ArrayList<>();
    public List<GoodsReceiptItem> getItems() { return items; }
    public void setItems(List<GoodsReceiptItem> value) { this.items = value; }
}
