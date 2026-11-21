package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="inventory_operations",uniqueConstraints=@UniqueConstraint(columnNames={"store_id","request_key"}))
public class InventoryOperation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="store_id",nullable=false) private Store store;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="performed_by_id",nullable=false) private UserAccount performedBy;
 @Column(nullable=false,length=100) private String requestKey;
 @Column(nullable=false,length=64) private String requestFingerprint;
 @Column(nullable=false,length=50) private String number;
 @Column(nullable=false,length=20) private String kind;
 @Column(nullable=false,length=255) private String productCode;
 @Column(nullable=false,length=255) private String productName;
 @Column(nullable=false) private Integer quantityBefore;
 @Column(nullable=false) private Integer quantityAfter;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal valueBefore;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal valueAfter;
 @Column(nullable=false,length=1000) private String note;
 @Column(nullable=false) private LocalDateTime createdAt;
 @OneToMany(mappedBy="operation",cascade=CascadeType.ALL) private List<InventoryOperationItem> items=new ArrayList<>();
 public Long getId(){return id;}
 public void setId(Long value){id=value;}
 public Store getStore(){return store;}
 public void setStore(Store value){store=value;}
 public UserAccount getPerformedBy(){return performedBy;}
 public void setPerformedBy(UserAccount value){performedBy=value;}
 public String getRequestKey(){return requestKey;}
 public void setRequestKey(String value){requestKey=value;}
 public String getRequestFingerprint(){return requestFingerprint;}
 public void setRequestFingerprint(String value){requestFingerprint=value;}
 public String getNumber(){return number;}
 public void setNumber(String value){number=value;}
 public String getKind(){return kind;}
 public void setKind(String value){kind=value;}
 public String getProductCode(){return productCode;}
 public void setProductCode(String value){productCode=value;}
 public String getProductName(){return productName;}
 public void setProductName(String value){productName=value;}
 public Integer getQuantityBefore(){return quantityBefore;}
 public void setQuantityBefore(Integer value){quantityBefore=value;}
 public Integer getQuantityAfter(){return quantityAfter;}
 public void setQuantityAfter(Integer value){quantityAfter=value;}
 public BigDecimal getValueBefore(){return valueBefore;}
 public void setValueBefore(BigDecimal value){valueBefore=value;}
 public BigDecimal getValueAfter(){return valueAfter;}
 public void setValueAfter(BigDecimal value){valueAfter=value;}
 public String getNote(){return note;}
 public void setNote(String value){note=value;}
 public LocalDateTime getCreatedAt(){return createdAt;}
 public void setCreatedAt(LocalDateTime value){createdAt=value;}
 public List<InventoryOperationItem> getItems(){return items;}
 public void setItems(List<InventoryOperationItem> value){items=value;}
}
