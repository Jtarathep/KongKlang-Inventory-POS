package com.kongklang.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="sale_tenders", uniqueConstraints=@UniqueConstraint(name="uk_sale_tender_method",columnNames={"sale_id","method"}))
public class SaleTender {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sale_id",nullable=false)
    @com.fasterxml.jackson.annotation.JsonIgnore private Sale sale;
    @Column(nullable=false,length=20) private String method;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal received;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal changeAmount;
    @Column(nullable=false,length=100) private String reference;
    public Long getId(){return id;}
    public Sale getSale(){return sale;} public void setSale(Sale v){sale=v;}
    public String getMethod(){return method;} public void setMethod(String v){method=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public BigDecimal getReceived(){return received;} public void setReceived(BigDecimal v){received=v;}
    public BigDecimal getChangeAmount(){return changeAmount;} public void setChangeAmount(BigDecimal v){changeAmount=v;}
    public String getReference(){return reference;} public void setReference(String v){reference=v;}
}
