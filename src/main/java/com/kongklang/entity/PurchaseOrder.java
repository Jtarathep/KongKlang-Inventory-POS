package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="purchase_orders",uniqueConstraints=@UniqueConstraint(name="uk_po_store_key",columnNames={"store_id","request_key"}))
public class PurchaseOrder {
    @Column(nullable=false,length=100)
    private String requestKey;
    @Column(nullable=false,length=64)
    private String requestFingerprint;
    public String getRequestKey(){return requestKey;}
    public void setRequestKey(String value){requestKey=value;}
    public String getRequestFingerprint(){return requestFingerprint;}
    public void setRequestFingerprint(String value){requestFingerprint=value;}
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;
    public Store getStore() { return store; }
    public void setStore(Store value) { this.store = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_id",nullable=false)
    private Supplier supplier;
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { this.supplier = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="orderedBy_id",nullable=false)
    private UserAccount orderedBy;
    public UserAccount getOrderedBy() { return orderedBy; }
    public void setOrderedBy(UserAccount value) { this.orderedBy = value; }
    @Column(nullable=false,unique=true)
    private String number;
    public String getNumber() { return number; }
    public void setNumber(String value) { this.number = value; }
    @Column(nullable=false)
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    @Column(nullable=false)
    private LocalDateTime createdAt;
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    @Column(length=1000)
    private String note;
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal total;
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { this.total = value; }
    @OneToMany(mappedBy="purchaseOrder", cascade=CascadeType.ALL)
    private List<PurchaseOrderItem> items = new ArrayList<>();
    public List<PurchaseOrderItem> getItems() { return items; }
    public void setItems(List<PurchaseOrderItem> value) { this.items = value; }
}
