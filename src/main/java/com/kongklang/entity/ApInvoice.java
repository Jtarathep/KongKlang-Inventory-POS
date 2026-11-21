package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="ap_invoices", uniqueConstraints={@UniqueConstraint(name="uk_invoice_receipt",columnNames="receipt_id"),@UniqueConstraint(name="uk_invoice_vendor",columnNames={"store_id","supplier_id","vendor_number"})})
public class ApInvoice {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="supplier_id",nullable=false)
    private Supplier supplier;
    public Supplier getSupplier() {return supplier;}
    public void setSupplier(Supplier value) {supplier=value;}
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;
    public Store getStore() { return store; }
    public void setStore(Store value) { this.store = value; }
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="receipt_id")
    private GoodsReceipt receipt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="inventory_operation_id",unique=true)
    private InventoryOperation inventoryOperation;
    public InventoryOperation getInventoryOperation(){return inventoryOperation;}
    public void setInventoryOperation(InventoryOperation value){inventoryOperation=value;}
    public GoodsReceipt getReceipt() { return receipt; }
    public void setReceipt(GoodsReceipt value) { this.receipt = value; }
    @Column(nullable=false,length=100)
    private String vendorNumber;
    public String getVendorNumber() { return vendorNumber; }
    public void setVendorNumber(String value) { this.vendorNumber = value; }
    @Column(nullable=false)
    private LocalDate invoiceDate;
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate value) { this.invoiceDate = value; }
    @Column(nullable=false)
    private LocalDate dueDate;
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate value) { this.dueDate = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal amount;
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { this.amount = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal paid = BigDecimal.ZERO;
    public BigDecimal getPaid() { return paid; }
    public void setPaid(BigDecimal value) { this.paid = value; }
    @Column(nullable=false)
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    @Column(nullable=false)
    private LocalDateTime createdAt;
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
