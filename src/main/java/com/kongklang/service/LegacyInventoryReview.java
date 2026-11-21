package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import com.kongklang.support.DecimalMoney;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** An explicit verified opening for pre-ledger products, never an automatic repair. */
@Service @Transactional
public class LegacyInventoryReview {
    private final ProductRepository products;private final StockBatchRepository batches;private final InventoryOperationRepository operations;
    private final StockLedgerService ledger;private final InventoryOperationService documents;private final JournalService journals;
    private final AuditTrail audit;private final JdbcTemplate jdbc;
    public LegacyInventoryReview(ProductRepository products,StockBatchRepository batches,InventoryOperationRepository operations,
            StockLedgerService ledger,InventoryOperationService documents,JournalService journals,AuditTrail audit,JdbcTemplate jdbc){
        this.products=products;this.batches=batches;this.operations=operations;this.ledger=ledger;this.documents=documents;this.journals=journals;this.audit=audit;this.jdbc=jdbc;
    }
    public InventoryOperationService.OperationView confirm(AccessService.Actor actor,Input input){
        if(actor==null||!Set.of("OWNER","ADMIN").contains(actor.role()))throw new IllegalArgumentException("เฉพาะผู้ดูแลร้านเท่านั้นที่ยืนยันยอดตั้งต้นได้");
        if(input==null||input.expectedQuantity()==null||input.expectedQuantity()<0||input.expectedVersion()==null||input.expectedVersion()<0||!Boolean.TRUE.equals(input.confirmed())||input.lots()==null||input.lots().size()>100)
            throw new IllegalArgumentException("กรุณายืนยันว่าตรวจนับจริง พร้อมต้นทุนและวันหมดอายุแต่ละล็อต");
        String code=PurchasingService.text(input.code(),255,"รหัสสินค้า"),key=PurchasingService.text(input.requestKey(),100,"รหัสป้องกันซ้ำ"),note=PurchasingService.text(input.note(),1000,"หลักฐานการตรวจนับและต้นทุน");
        if(!key.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("รหัสป้องกันซ้ำไม่ถูกต้อง");
        long total=0;StringBuilder canonical=new StringBuilder(actor.userId()+"|"+code.toUpperCase(Locale.ROOT)+"|"+input.expectedQuantity()+"|"+input.expectedVersion()+"|"+note.length()+":"+note);
        for(Lot lot:input.lots()) {
            if(lot==null||lot.quantity()==null||lot.quantity()<=0)throw new IllegalArgumentException("จำนวนในแต่ละล็อตต้องมากกว่า 0");
            BigDecimal cost=DecimalMoney.required(DecimalMoney.normalize(lot.unitCost(),4),"ต้นทุนจริงแต่ละล็อต");
            total=Math.addExact(total,lot.quantity());if(total>Integer.MAX_VALUE)throw new IllegalArgumentException("จำนวนรวมมากเกินไป");
            canonical.append('|').append(lot.quantity()).append(':').append(cost.toPlainString()).append(':').append(lot.expiryDate());
            if(lot.expiryDate()!=null&&lot.expiryDate().isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"))))throw new IllegalArgumentException("ยอดตั้งต้นต้องแยกสินค้าที่หมดอายุออกก่อน");
        }
        String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        Product product=ledger.lockProduct(code,actor.storeId());
        var prior=operations.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent()) {
            if(!prior.get().getKind().equals("BASELINE")||!prior.get().getRequestFingerprint().equals(fingerprint))throw new IllegalArgumentException("รหัสเดิมใช้กับข้อมูลอื่นแล้ว");
            return documents.detail(actor,prior.get().getId());
        }
        if(!Objects.equals(product.getQuantity(),input.expectedQuantity())||!Objects.equals(product.getVersion(),input.expectedVersion()))throw new IllegalArgumentException("ข้อมูลเปลี่ยนแล้ว กรุณาโหลดและตรวจนับใหม่");
        Integer modern=jdbc.queryForObject("select (select count(*) from inventory_operations where store_id=? and product_code=?) + (select count(*) from sale_allocations a join sale_items i on i.id=a.sale_item_id where a.store_id=? and i.product_code=?) + (select count(*) from goods_receipt_items i join stock_batches b on b.id=i.stock_batch_id where b.store_id=? and b.product_code=?)",Integer.class,actor.storeId(),product.getCode(),actor.storeId(),product.getCode(),actor.storeId(),product.getCode());
        if(modern!=null&&modern>0)throw new IllegalArgumentException("สินค้านี้มีรายการในระบบใหม่แล้ว ให้ใช้การตรวจนับปกติ");
        List<StockBatch> old=batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(actor.storeId(),product.getCode());
        BigDecimal before=old.stream().map(StockBatch::getRemainingValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        StringJoiner snapshot=new StringJoiner(",","{\"productQuantity\":"+product.getQuantity()+",\"version\":"+product.getVersion()+",\"batches\":[","]}");
        for(StockBatch b:old)snapshot.add("{\"id\":"+b.getId()+",\"received\":"+b.getQuantity()+",\"remaining\":"+b.getRemainingQuantity()+",\"receivedValue\":"+b.getReceivedValue()+",\"remainingValue\":"+b.getRemainingValue()+",\"unitCost\":"+b.getPurchaseCostValue()+",\"expiry\":"+(b.getExpiryDate()==null?"null":"\""+b.getExpiryDate()+"\"")+"}");
        InventoryOperation op=new InventoryOperation();op.setStore(product.getStore());op.setPerformedBy(PurchasingService.user(actor.userId()));op.setKind("BASELINE");op.setRequestKey(key);op.setRequestFingerprint(fingerprint);
        op.setNumber(PurchasingService.number("OPEN"));op.setProductCode(product.getCode());op.setProductName(product.getName());op.setQuantityBefore(product.getQuantity());op.setQuantityAfter((int)total);op.setValueBefore(before);op.setValueAfter(BigDecimal.ZERO);op.setNote(note);op.setCreatedAt(PurchasingService.now());operations.saveAndFlush(op);
        jdbc.update("insert into legacy_inventory_reviews(operation_id,store_id,snapshot) values(?,?,?)",op.getId(),actor.storeId(),snapshot.toString());
        for(StockBatch b:old){add(op,b,-b.getRemainingQuantity(),b.getRemainingValue().negate());b.setRemainingQuantity(0);b.setRemainingValue(BigDecimal.ZERO);batches.save(b);}
        jdbc.update("insert into stock_transactions(product_code,product_name,type,quantity,balance,note,store_id,created_at) values(?,?,'BASELINE',?,0,?,?,?)",product.getCode(),product.getName(),-product.getQuantity(),op.getNumber()+" / ยืนยันยอดตั้งต้น",actor.storeId(),PurchasingService.now());
        product.setQuantity(0);products.saveAndFlush(product);
        BigDecimal value=BigDecimal.ZERO;
        for(Lot lot:input.lots()) {
            StockBatch b=ledger.receiveBatchValue(product.getCode(),lot.quantity(),lot.unitCost(),null,lot.expiryDate(),op.getNumber()+" / "+note,actor.storeId());
            add(op,b,b.getRemainingQuantity(),b.getRemainingValue());value=value.add(b.getRemainingValue());
        }
        op.setValueAfter(value);operations.saveAndFlush(op);
        journals.post(actor.storeId(),"INVENTORY_BASELINE",op.getId(),op.getNumber(),"1200","3100",value);
        audit.record(actor,"LEGACY_INVENTORY_BASELINE",op.getNumber());return documents.detail(actor,op.getId());
    }
    private void add(InventoryOperation op,StockBatch batch,int quantity,BigDecimal value){
        if(quantity==0&&value.signum()==0)return;
        InventoryOperationItem item=new InventoryOperationItem();item.setOperation(op);item.setStockBatch(batch);item.setQuantityChange(quantity);item.setValueChange(value);item.setUnitCost(batch.getPurchaseCostValue());op.getItems().add(item);
    }
    @Transactional(readOnly=true) public String snapshot(Long storeId,Long operationId){
        return jdbc.query("select snapshot from legacy_inventory_reviews where store_id=? and operation_id=?",(rs,n)->rs.getString(1),storeId,operationId).stream().findFirst().orElse(null);
    }
    public record Lot(Integer quantity,BigDecimal unitCost,LocalDate expiryDate){}
    public record Input(String requestKey,String code,Integer expectedQuantity,Long expectedVersion,Boolean confirmed,String note,List<Lot> lots){}
}
