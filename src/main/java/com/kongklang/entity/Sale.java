package com.kongklang.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import com.kongklang.support.DecimalMoney;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales",uniqueConstraints=@UniqueConstraint(name="uk_sale_store_request",columnNames={"store_id","request_key"}))
public class Sale {
    @Column(length=20) private String documentType;
    public String getDocumentType(){return documentType==null?"RECEIPT":documentType;}
    public void setDocumentType(String v){documentType=v;}
    @Column(length=255) private String sellerName;
    public String getSellerName(){return sellerName;} public void setSellerName(String v){sellerName=v;}
    @Column(length=1000) private String sellerAddress;
    public String getSellerAddress(){return sellerAddress;} public void setSellerAddress(String v){sellerAddress=v;}
    @Column(length=13) private String sellerTaxId;
    public String getSellerTaxId(){return sellerTaxId;} public void setSellerTaxId(String v){sellerTaxId=v;}
    @Column(length=5) private String sellerBranch;
    public String getSellerBranch(){return sellerBranch;} public void setSellerBranch(String v){sellerBranch=v;}
    @Column(length=255) private String buyerName;
    public String getBuyerName(){return buyerName;} public void setBuyerName(String v){buyerName=v;}
    @Column(length=1000) private String buyerAddress;
    public String getBuyerAddress(){return buyerAddress;} public void setBuyerAddress(String v){buyerAddress=v;}
    @Column(length=13) private String buyerTaxId;
    public String getBuyerTaxId(){return buyerTaxId;} public void setBuyerTaxId(String v){buyerTaxId=v;}
    @Column(length=5) private String buyerBranch;
    public String getBuyerBranch(){return buyerBranch;} public void setBuyerBranch(String v){buyerBranch=v;}

    @Column(precision=19,scale=2) private BigDecimal listedAmount;
    @Column(precision=19,scale=2) private BigDecimal discountAmount;
    @Column(precision=19,scale=2) private BigDecimal netAmount;
    @Column(precision=19,scale=2) private BigDecimal taxAmount;
    @Column(precision=5,scale=2) private BigDecimal taxRate;
    @Column(length=20) private String taxMode;
    @Column(length=20) private String discountType;
    @Column(precision=19,scale=2) private BigDecimal discountValue;
    @OneToMany(mappedBy="sale",cascade=CascadeType.ALL) private java.util.List<SaleTender> tenders=new java.util.ArrayList<>();
    public java.util.List<SaleTender> getTenders(){return tenders;}
    public BigDecimal getListedAmount(){return listedAmount==null?totalAmount:listedAmount;}
    public void setListedAmount(BigDecimal v){listedAmount=v;}
    public BigDecimal getDiscountAmount(){return discountAmount==null?BigDecimal.ZERO:discountAmount;}
    public void setDiscountAmount(BigDecimal v){discountAmount=v;}
    public BigDecimal getNetAmount(){return netAmount==null?totalAmount:netAmount;}
    public void setNetAmount(BigDecimal v){netAmount=v;}
    public BigDecimal getTaxAmount(){return taxAmount==null?BigDecimal.ZERO:taxAmount;}
    public void setTaxAmount(BigDecimal v){taxAmount=v;}
    public BigDecimal getTaxRate(){return taxRate==null?BigDecimal.ZERO:taxRate;}
    public void setTaxRate(BigDecimal v){taxRate=v;}
    public String getTaxMode(){return taxMode==null?"NONE":taxMode;}
    public void setTaxMode(String v){taxMode=v;}
    public String getDiscountType(){return discountType==null?"NONE":discountType;}
    public void setDiscountType(String v){discountType=v;}
    public BigDecimal getDiscountValue(){return discountValue==null?BigDecimal.ZERO:discountValue;}
    public void setDiscountValue(BigDecimal v){discountValue=v;}
    @Column(length=100) private String requestKey;
    @Column(length=64) private String requestFingerprint;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="sold_by_id") private UserAccount soldBy;
    @Column(length=20) private String paymentMethod;
    @Column(length=20) private String paymentStatus;
    @Column(precision=19,scale=2) private BigDecimal totalCost;
    public String getRequestKey(){return requestKey;}
    public void setRequestKey(String value){requestKey=value;}
    @com.fasterxml.jackson.annotation.JsonIgnore public String getRequestFingerprint(){return requestFingerprint;}
    public void setRequestFingerprint(String value){requestFingerprint=value;}
    @com.fasterxml.jackson.annotation.JsonIgnore public UserAccount getSoldBy(){return soldBy;}
    public void setSoldBy(UserAccount value){soldBy=value;}
    public String getPaymentMethod(){return paymentMethod;}
    public void setPaymentMethod(String value){paymentMethod=value;}
    public String getPaymentStatus(){return paymentStatus;}
    public void setPaymentStatus(String value){paymentStatus=value;}
    public BigDecimal getTotalCost(){return totalCost;}
    public void setTotalCost(BigDecimal value){totalCost=DecimalMoney.normalize(value,2);}


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // เลขที่บิล
    @Column(nullable = false, unique = true)
    private String saleNumber;

    // ยอดรวม
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // ร้านเจ้าของบิล (nullable เพื่อรองรับบิลเก่าที่ยังไม่ทราบร้าน)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    // วันที่ขาย
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Constructor
    public Sale() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Bangkok"));
    }

    public Long getId() {
        return id;
    }

    public String getSaleNumber() {
        return saleNumber;
    }

    public void setSaleNumber(String saleNumber) {
        this.saleNumber = saleNumber;
    }

    public Double getTotalAmount() { return totalAmount == null ? null : totalAmount.doubleValue(); }

    public void setTotalAmount(Double totalAmount) { this.totalAmount = DecimalMoney.legacy(totalAmount, 2); }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getTotalAmountValue() { return totalAmount; }
    public void setTotalAmountValue(BigDecimal value) { this.totalAmount = DecimalMoney.normalize(value, 2); }
}
