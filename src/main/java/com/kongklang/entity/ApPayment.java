package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="ap_payments", uniqueConstraints=@UniqueConstraint(name="uk_payment_store_key",columnNames={"store_id","request_key"}))
public class ApPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;
    public Store getStore() { return store; }
    public void setStore(Store value) { this.store = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="invoice_id",nullable=false)
    private ApInvoice invoice;
    public ApInvoice getInvoice() { return invoice; }
    public void setInvoice(ApInvoice value) { this.invoice = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="paidBy_id",nullable=false)
    private UserAccount paidBy;
    public UserAccount getPaidBy() { return paidBy; }
    public void setPaidBy(UserAccount value) { this.paidBy = value; }
    @Column(nullable=false,length=100)
    private String requestKey;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    @Column(nullable=false,unique=true)
    private String number;
    public String getNumber() { return number; }
    public void setNumber(String value) { this.number = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal amount;
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { this.amount = value; }
    @Column(nullable=false)
    private LocalDate paymentDate;
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate value) { this.paymentDate = value; }
    @Column(length=1000)
    private String note;
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
    @Column(nullable=false)
    private LocalDateTime createdAt;
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
