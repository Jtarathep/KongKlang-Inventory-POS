package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="sale_allocations",uniqueConstraints=@UniqueConstraint(name="uk_sale_item_batch",columnNames={"sale_item_id","batch_id"}))
public class SaleAllocation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="store_id",nullable=false) private Store store;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sale_item_id",nullable=false) private SaleItem saleItem;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="batch_id",nullable=false) private StockBatch batch;
    @Column(nullable=false) private Integer quantity;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal unitCost;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal actualCost;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal financialCost;
    public BigDecimal getFinancialCost(){return financialCost;}
    public void setFinancialCost(BigDecimal value){financialCost=value;}
    public Long getId(){return id;}
    public Store getStore(){return store;} public void setStore(Store value){store=value;}
    public SaleItem getSaleItem(){return saleItem;} public void setSaleItem(SaleItem value){saleItem=value;}
    public StockBatch getBatch(){return batch;} public void setBatch(StockBatch value){batch=value;}
    public Integer getQuantity(){return quantity;} public void setQuantity(Integer value){quantity=value;}
    public BigDecimal getUnitCost(){return unitCost;} public void setUnitCost(BigDecimal value){unitCost=value;}
    public BigDecimal getActualCost(){return actualCost;} public void setActualCost(BigDecimal value){actualCost=value;}
}
