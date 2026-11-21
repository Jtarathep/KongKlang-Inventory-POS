package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="inventory_operation_items",uniqueConstraints=@UniqueConstraint(columnNames={"operation_id","stock_batch_id"}))
public class InventoryOperationItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="operation_id",nullable=false) private InventoryOperation operation;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="stock_batch_id",nullable=false) private StockBatch stockBatch;
 @Column(nullable=false) private Integer quantityChange;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal valueChange;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal unitCost;
 public Long getId(){return id;}
 public void setId(Long value){id=value;}
 public InventoryOperation getOperation(){return operation;}
 public void setOperation(InventoryOperation value){operation=value;}
 public StockBatch getStockBatch(){return stockBatch;}
 public void setStockBatch(StockBatch value){stockBatch=value;}
 public Integer getQuantityChange(){return quantityChange;}
 public void setQuantityChange(Integer value){quantityChange=value;}
 public BigDecimal getValueChange(){return valueChange;}
 public void setValueChange(BigDecimal value){valueChange=value;}
 public BigDecimal getUnitCost(){return unitCost;}
 public void setUnitCost(BigDecimal value){unitCost=value;}
}
