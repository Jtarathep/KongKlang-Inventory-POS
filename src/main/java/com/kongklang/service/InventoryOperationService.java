package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import com.kongklang.support.DecimalMoney;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** Non-sale inventory events. Operation, lot changes and journal commit together. */
@Service
@Transactional
public class InventoryOperationService {
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}

    private final InventoryOperationRepository operations;
    private final StockLedgerService ledger;
    private final JournalService journals;
    public InventoryOperationService(InventoryOperationRepository operations,StockLedgerService ledger,JournalService journals) {
        this.operations=operations;this.ledger=ledger;this.journals=journals;
    }

    public OperationView execute(AccessService.Actor actor,Input input) {
        requireActor(actor,false);
        if(input==null||!Set.of("RECEIPT","OUT","ADJUST").contains(input.kind()==null?"":input.kind()))
            throw new IllegalArgumentException("กรุณาระบุประเภทรายการสต็อก");
        return apply(actor,input);
    }

    // Only product creation calls this; the public operation API cannot post opening equity.
    OperationView opening(AccessService.Actor actor,String code,int quantity,BigDecimal cost,Long productId) {
        requireActor(actor,true);
        return apply(actor,new Input("OPEN-"+productId,"OPENING",code,quantity,0,null,cost,null,null,"ยอดตั้งต้นจากการสร้างสินค้า"));
    }

    private OperationView apply(AccessService.Actor actor,Input input) {
        String key=PurchasingService.text(input.requestKey(),100,"รหัสป้องกันรายการซ้ำ");
        if(!key.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("รหัสป้องกันรายการซ้ำไม่ถูกต้อง");
        String code=PurchasingService.text(input.code(),255,"รหัสสินค้า");
        String note=PurchasingService.text(input.note(),1000,"เหตุผลหรือเอกสารอ้างอิง");
        if(input.quantity()==null||input.quantity()<0||(!input.kind().equals("ADJUST")&&input.quantity()==0))
            throw new IllegalArgumentException("จำนวนสินค้าไม่ถูกต้อง");
        boolean receipt=Set.of("RECEIPT","OPENING").contains(input.kind());
        if(!receipt&&!input.kind().equals("ADJUST")&&(input.unitCost()!=null||input.sellingPrice()!=null||input.expiryDate()!=null))
            throw new IllegalArgumentException("ราคาหรือวันหมดอายุใช้เฉพาะรายการรับเข้า");
        if(input.kind().equals("ADJUST")&&(input.sellingPrice()!=null||((input.unitCost()!=null||input.expiryDate()!=null)&&input.expectedQuantity()!=null&&input.quantity()<=input.expectedQuantity())))
            throw new IllegalArgumentException("ต้นทุนและวันหมดอายุใช้เฉพาะการตรวจนับเพิ่ม");
        if(receipt&&input.expectedQuantity()!=null&&!input.kind().equals("OPENING"))
            throw new IllegalArgumentException("ยอดก่อนตรวจนับใช้เฉพาะการปรับยอด");
        if(input.kind().equals("OUT")&&input.expectedQuantity()!=null)
            throw new IllegalArgumentException("รายการเบิกออกไม่รับยอดตรวจนับ");
        if(input.kind().equals("ADJUST")&&(input.expectedQuantity()==null||input.expectedQuantity()<0||input.expectedVersion()==null||input.expectedVersion()<0))
            throw new IllegalArgumentException("กรุณาระบุยอดระบบที่ใช้เริ่มตรวจนับ");
        if(!input.kind().equals("ADJUST")&&input.expectedVersion()!=null)throw new IllegalArgumentException("เวอร์ชันตรวจนับใช้เฉพาะการปรับยอด");
        BigDecimal cost=DecimalMoney.normalize(input.unitCost(),4);
        BigDecimal price=DecimalMoney.normalize(input.sellingPrice(),2);
        String fingerprint=fingerprint(actor,input,code,note,cost,price);
        Optional<InventoryOperation> prior=operations.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent())return replay(prior.get(),fingerprint);

        Product product=ledger.lockProduct(code,actor.storeId());
        // Same-product concurrent submissions serialize on the product; unique(store,key) covers different products.
        prior=operations.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent())return replay(prior.get(),fingerprint);
        if(receipt&&cost==null)cost=DecimalMoney.required(product.getCostPriceValue(),"กรุณาระบุต้นทุนรับเข้า");
        StockLedgerService.Snapshot before=ledger.snapshot(product.getCode(),actor.storeId());
        if(Set.of("ADJUST","OPENING").contains(input.kind())&&input.expectedQuantity()!=before.quantity())
            throw new IllegalArgumentException("ยอดระบบเปลี่ยนแล้ว กรุณาโหลดข้อมูลและตรวจนับใหม่ก่อนปรับยอด");
        if(input.kind().equals("ADJUST")&&!input.expectedVersion().equals(product.getVersion()))
            throw new IllegalArgumentException("ข้อมูลสินค้าเปลี่ยนแล้ว กรุณาโหลดข้อมูลและตรวจนับใหม่");
        if(input.kind().equals("OPENING")&&(!before.batches().isEmpty()||before.quantity()!=0))
            throw new IllegalArgumentException("ยอดตั้งต้นใช้ได้เฉพาะสินค้าใหม่ที่ยังไม่มีล็อต");

        InventoryOperation op=new InventoryOperation();op.setStore(PurchasingService.store(actor.storeId()));
        op.setPerformedBy(PurchasingService.user(actor.userId()));op.setRequestKey(key);op.setRequestFingerprint(fingerprint);
        op.setNumber(PurchasingService.number("IV"));op.setKind(input.kind());op.setProductCode(product.getCode());op.setProductName(product.getName());
        op.setQuantityBefore(before.quantity());op.setQuantityAfter(before.quantity());op.setValueBefore(before.value());op.setValueAfter(before.value());
        op.setNote(note);op.setCreatedAt(PurchasingService.now());operations.saveAndFlush(op);
        String ledgerNote=op.getNumber()+" / "+note;
        if(receipt)ledger.receiveBatchValue(product.getCode(),input.quantity(),cost,price,input.expiryDate(),ledgerNote,actor.storeId());
        else if(input.kind().equals("OUT"))ledger.consume(product.getCode(),input.quantity(),ledgerNote,actor.storeId(),true);
        else if(input.unitCost()!=null||input.expiryDate()!=null)ledger.adjust(product.getCode(),input.quantity(),ledgerNote,actor.storeId(),cost,input.expiryDate());
        else ledger.adjust(product.getCode(),input.quantity(),ledgerNote,actor.storeId());
        StockLedgerService.Snapshot after=ledger.snapshot(product.getCode(),actor.storeId());
        op.setQuantityAfter(after.quantity());op.setValueAfter(after.value());
        auditLots(op,before,after);
        BigDecimal delta=after.value().subtract(before.value());
        if(delta.signum()!=0)BusinessMoney.amount(delta.abs());
        String debit,credit;
        if(input.kind().equals("OPENING")){debit="1200";credit="3100";}
        else if(input.kind().equals("RECEIPT")){debit="1200";credit="2110";}
        else if(delta.signum()>0){debit="1200";credit="4100";}
        else {debit="5100";credit="1200";}
        journals.post(actor.storeId(),"INVENTORY_"+input.kind(),op.getId(),op.getNumber(),debit,credit,delta.abs());
        operations.saveAndFlush(op);
        if(audit!=null)audit.record(actor,"INVENTORY_"+op.getKind(),op.getNumber());
        return view(op);
    }

    private void auditLots(InventoryOperation op,StockLedgerService.Snapshot before,StockLedgerService.Snapshot after) {
        Map<Long,StockLedgerService.BatchSnapshot> old=new HashMap<>();
        before.batches().forEach(b->old.put(b.id(),b));
        int qty=0;BigDecimal value=BigDecimal.ZERO;
        for(var b:after.batches()) {
            var prior=old.remove(b.id());
            int dq=b.quantity()-(prior==null?0:prior.quantity());
            BigDecimal dv=b.value().subtract(prior==null?BigDecimal.ZERO:prior.value());
            if(dq==0&&dv.signum()==0)continue;
            InventoryOperationItem item=new InventoryOperationItem();item.setOperation(op);
            StockBatch batch=new StockBatch();batch.setId(b.id());item.setStockBatch(batch);
            item.setQuantityChange(dq);item.setValueChange(dv);item.setUnitCost(b.unitCost());op.getItems().add(item);
            qty=Math.addExact(qty,dq);value=value.add(dv);
        }
        if(!old.isEmpty()||qty!=Math.subtractExact(after.quantity(),before.quantity())||value.compareTo(after.value().subtract(before.value()))!=0)
            throw new IllegalStateException("มูลค่าหรือจำนวนล็อตไม่ตรงกับเอกสารสต็อก");
    }
    private OperationView replay(InventoryOperation prior,String fingerprint) {
        if(!prior.getRequestFingerprint().equals(fingerprint))throw new IllegalArgumentException("รหัสรายการเดิมถูกใช้กับข้อมูลหรือผู้ทำรายการต่างกัน");
        return view(prior);
    }
    @Transactional(readOnly=true)
    public List<OperationView> list(AccessService.Actor actor) {
        requireActor(actor,false,true);
        return operations.findTop100ByStoreIdOrderByIdDesc(actor.storeId()).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<OperationView> search(AccessService.Actor actor,LocalDate start,LocalDate end,String code,int page) {
        requireActor(actor,false,true);
        if(start==null||end==null||end.isBefore(start)||page<0||page>100000)throw new IllegalArgumentException("ช่วงวันที่หรือหน้าไม่ถูกต้อง");
        return operations.findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndProductCodeContainingIgnoreCaseOrderByIdDesc(
            actor.storeId(),start.atStartOfDay(),end.plusDays(1).atStartOfDay(),code==null?"":code.trim(),org.springframework.data.domain.PageRequest.of(page,25)).map(this::view);
    }
    @Transactional(readOnly=true)
    public OperationView detail(AccessService.Actor actor,Long id) {
        requireActor(actor,false,true);
        return view(operations.findByIdAndStoreId(id,actor.storeId()).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)));
    }
    private void requireActor(AccessService.Actor actor,boolean opening){requireActor(actor,opening,false);}
    private void requireActor(AccessService.Actor actor,boolean opening,boolean read) {
        Set<String> roles=opening?Set.of("OWNER","ADMIN"):read?Set.of("OWNER","ADMIN","WAREHOUSE","ACCOUNTING"):Set.of("OWNER","ADMIN","WAREHOUSE");
        if(actor==null||actor.userId()==null||actor.storeId()==null||!roles.contains(actor.role()))
            throw new IllegalArgumentException("ไม่มีสิทธิ์ทำรายการสต็อกนี้");
    }
    private String fingerprint(AccessService.Actor actor,Input i,String code,String note,BigDecimal cost,BigDecimal price) {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);
            data.writeLong(actor.userId());
            for(String part:List.of(i.kind(),code.toUpperCase(Locale.ROOT),i.quantity().toString(),
                    i.expectedQuantity()==null?"":i.expectedQuantity().toString(),i.expectedVersion()==null?"":i.expectedVersion().toString(),cost==null?"":cost.toPlainString(),
                    price==null?"":price.toPlainString(),i.expiryDate()==null?"":i.expiryDate().toString(),note))data.writeUTF(part);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private OperationView view(InventoryOperation o) {
        return new OperationView(o.getId(),o.getNumber(),o.getKind(),o.getProductCode(),o.getProductName(),o.getQuantityBefore(),o.getQuantityAfter(),
            o.getValueBefore(),o.getValueAfter(),o.getPerformedBy().getId(),o.getNote(),o.getCreatedAt(),o.getItems().stream().map(i->new LotView(
                i.getStockBatch().getId(),i.getQuantityChange(),i.getValueChange(),i.getUnitCost())).toList());
    }
    public record Input(String requestKey,String kind,String code,Integer quantity,Integer expectedQuantity,Long expectedVersion,BigDecimal unitCost,BigDecimal sellingPrice,LocalDate expiryDate,String note){}
    public record LotView(Long batchId,Integer quantityChange,BigDecimal valueChange,BigDecimal unitCost){}
    public record OperationView(Long id,String number,String kind,String productCode,String productName,Integer quantityBefore,Integer quantityAfter,
        BigDecimal valueBefore,BigDecimal valueAfter,Long performedBy,String note,java.time.LocalDateTime createdAt,List<LotView> lots){}
}
