package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.sql.*;
import java.util.*;

@Service
@Transactional
public class SaleReturnService {
    private final StoreRepository stores;private final SaleRepository sales;private final SaleItemRepository items;
    private final SaleAllocationRepository allocations;private final StockLedgerService ledger;
    private final JournalService journals;private final AuditTrail audit;private final JdbcTemplate jdbc;
    public SaleReturnService(StoreRepository stores,SaleRepository sales,SaleItemRepository items,SaleAllocationRepository allocations,
            StockLedgerService ledger,JournalService journals,AuditTrail audit,JdbcTemplate jdbc){
        this.stores=stores;this.sales=sales;this.items=items;this.allocations=allocations;this.ledger=ledger;this.journals=journals;this.audit=audit;this.jdbc=jdbc;
    }
    public record LineInput(Long itemId,int quantity,boolean restock){}
    public record Input(String requestKey,List<LineInput> lines,List<CheckoutTerms.Payment> payments,
                        BigDecimal expectedTotal,Long revision,boolean confirmed,String reason){}
    public record Returnable(Long itemId,String code,String name,int sold,int returned,int remaining,BigDecimal paid){}
    public record QuoteLine(Long itemId,String code,String name,int quantity,boolean restock,BigDecimal net,BigDecimal tax,BigDecimal total){}
    public record Quote(long revision,List<QuoteLine> lines,BigDecimal net,BigDecimal tax,BigDecimal total){}
    public record View(Long id,String number,Long saleId,String reason,java.time.LocalDateTime createdAt,BigDecimal net,BigDecimal tax,BigDecimal total,BigDecimal restockedCost){}
    private record AllocationPlan(SaleAllocation allocation,int quantity,BigDecimal cost){}
    private record Plan(SaleItem item,int quantity,boolean restock,SalePricing.Refund money,List<AllocationPlan> allocations){}
    private record Prepared(Quote quote,List<Plan> plans){}
    private void manager(AccessService.Actor actor){if(actor==null||!Set.of("OWNER","ADMIN").contains(actor.role()))throw new IllegalArgumentException("การคืนเงินต้องให้เจ้าของร้านหรือแอดมินยืนยัน");}
    private Sale sale(Long saleId,Long storeId){return sales.findByIdAndStoreId(saleId,storeId).orElseThrow(()->new IllegalArgumentException("ไม่พบบิลในร้าน"));}
    private long revision(Long saleId,Long storeId){return jdbc.queryForObject("select count(*) from sale_returns where sale_id=? and store_id=?",Long.class,saleId,storeId);}
    @Transactional(readOnly=true)
    public List<Returnable> available(Long saleId,Long storeId){
        sale(saleId,storeId);
        return items.findBySaleIdAndSaleStoreId(saleId,storeId).stream().map(item->{int returned=returned(item.getId());return new Returnable(item.getId(),item.getProductCode(),item.getProductName(),item.getQuantity(),returned,item.getQuantity()-returned,item.getSubtotalValue());}).toList();
    }
    public Quote quote(AccessService.Actor actor,Long saleId,List<LineInput> lines){manager(actor);stores.lockById(actor.storeId()).orElseThrow();sale(saleId,actor.storeId());return prepare(saleId,actor.storeId(),lines).quote();}
    private int returned(Long itemId){return jdbc.queryForObject("select coalesce(sum(quantity),0) from sale_return_items where sale_item_id=?",Integer.class,itemId);}
    private Prepared prepare(Long saleId,Long storeId,List<LineInput> input){
        if(input==null||input.isEmpty()||input.size()>200)throw new IllegalArgumentException("กรุณาเลือกสินค้าคืน 1 ถึง 200 รายการ");
        Map<Long,SaleItem> sold=new HashMap<>();items.findBySaleIdAndSaleStoreId(saleId,storeId).forEach(i->sold.put(i.getId(),i));
        List<SaleAllocation> source=allocations.findBySaleItemSaleIdAndStoreIdOrderByIdAsc(saleId,storeId);
        Set<Long> seen=new HashSet<>();List<Plan> plans=new ArrayList<>();List<QuoteLine> quoted=new ArrayList<>();
        BigDecimal net=BigDecimal.ZERO,tax=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(LineInput line:input){
            if(line==null||line.itemId()==null||line.quantity()<=0||!seen.add(line.itemId())||!sold.containsKey(line.itemId()))throw new IllegalArgumentException("สินค้าคืนซ้ำหรือไม่อยู่ในบิล");
            SaleItem item=sold.get(line.itemId());int before=returned(item.getId());
            SalePricing.Line snapshot=new SalePricing.Line(item.getProductCode(),item.getQuantity(),item.getPriceValue(),item.getListedAmount(),item.getDiscountAmount(),item.getNetAmount(),item.getTaxAmount(),item.getSubtotalValue());
            SalePricing.Refund money=SalePricing.refund(snapshot,before,line.quantity());
            List<SaleAllocation> lots=source.stream().filter(a->a.getSaleItem().getId().equals(item.getId())).toList();
            if(lots.stream().mapToLong(SaleAllocation::getQuantity).sum()!=item.getQuantity())throw new IllegalArgumentException("บิลเก่าหรือข้อมูลล็อตไม่ครบ จึงยังคืนผ่านระบบอัตโนมัติไม่ได้");
            int remaining=line.quantity();List<AllocationPlan> batchPlans=new ArrayList<>();
            for(SaleAllocation allocation:lots){
                int used=jdbc.queryForObject("select coalesce(sum(quantity),0) from sale_return_allocations where sale_allocation_id=?",Integer.class,allocation.getId());
                if(used<0||used>allocation.getQuantity())throw new IllegalStateException("ยอดคืนล็อตไม่ถูกต้อง");
                int take=Math.min(remaining,allocation.getQuantity()-used);if(take==0)continue;
                if(line.restock()&&allocation.getBatch().getExpiryDate()!=null&&allocation.getBatch().getExpiryDate().isBefore(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"))))throw new IllegalArgumentException("สินค้าที่หมดอายุแล้วให้เลือกไม่เข้าสต๊อก");
                BigDecimal cost=cumulative(allocation.getFinancialCost(),used+take,allocation.getQuantity()).subtract(cumulative(allocation.getFinancialCost(),used,allocation.getQuantity()));
                batchPlans.add(new AllocationPlan(allocation,take,cost));remaining-=take;if(remaining==0)break;
            }
            if(remaining!=0)throw new IllegalStateException("จำนวนคืนไม่ตรงกับล็อตเดิม");
            plans.add(new Plan(item,line.quantity(),line.restock(),money,List.copyOf(batchPlans)));
            quoted.add(new QuoteLine(item.getId(),item.getProductCode(),item.getProductName(),line.quantity(),line.restock(),money.net(),money.tax(),money.payable()));
            net=net.add(money.net());tax=tax.add(money.tax());total=total.add(money.payable());
        }
        return new Prepared(new Quote(revision(saleId,storeId),List.copyOf(quoted),net,tax,total),List.copyOf(plans));
    }
    private BigDecimal cumulative(BigDecimal value,int quantity,int total){return value.multiply(BigDecimal.valueOf(quantity)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);}
    public View refund(AccessService.Actor actor,Long saleId,Input input){
        manager(actor);if(input==null||input.revision()==null)throw new IllegalArgumentException("กรุณาตรวจสอบยอดคืนก่อนบันทึก");
        String key=PurchasingService.text(input.requestKey(),100,"รหัสป้องกันคืนซ้ำ"),reason=PurchasingService.text(input.reason(),1000,"เหตุผลคืนสินค้า");
        CheckoutTerms payment=new CheckoutTerms(null,input.payments(),input.expectedTotal(),input.confirmed());
        for(var tender:payment.payments())if(tender.change().signum()!=0)throw new IllegalArgumentException("ยอดคืนเงินต้องตรงกับยอดจ่ายจริง ไม่มีเงินทอน");
        String fingerprint=fingerprint(actor,saleId,input,payment,reason);
        stores.lockById(actor.storeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"));
        var prior=jdbc.queryForList("select id,fingerprint from sale_returns where store_id=? and request_key=?",actor.storeId(),key);
        if(!prior.isEmpty()){
            if(!fingerprint.equals(prior.getFirst().get("fingerprint")))throw new IllegalArgumentException("รหัสคืนเงินนี้ถูกใช้กับข้อมูลอื่นแล้ว");
            return get(((Number)prior.getFirst().get("id")).longValue(),actor.storeId());
        }
        Sale original=sale(saleId,actor.storeId());
        if(!"PAID".equals(original.getPaymentStatus()))throw new IllegalArgumentException("บิลนี้ไม่มีสถานะชำระแล้วที่ตรวจสอบได้");
        Prepared prepared=prepare(saleId,actor.storeId(),input.lines());Quote q=prepared.quote();
        if(q.revision()!=input.revision()||q.total().compareTo(payment.expectedTotal())!=0)throw new IllegalArgumentException("ยอดคืนเปลี่ยนแล้ว กรุณาตรวจสอบรายการใหม่");
        String number=PurchasingService.number("R");var now=PurchasingService.now();
        BigDecimal cost=prepared.plans().stream().filter(Plan::restock).flatMap(p->p.allocations().stream()).map(AllocationPlan::cost).reduce(BigDecimal.ZERO,BigDecimal::add);
        Long id=insert("insert into sale_returns(store_id,sale_id,performed_by_id,request_key,fingerprint,number,reason,created_at,net_amount,tax_amount,total_amount,restocked_cost) values(?,?,?,?,?,?,?,?,?,?,?,?)",actor.storeId(),saleId,actor.userId(),key,fingerprint,number,reason,Timestamp.valueOf(now),q.net(),q.tax(),q.total(),cost);
        for(Plan plan:prepared.plans()){
            BigDecimal lineCost=plan.allocations().stream().map(AllocationPlan::cost).reduce(BigDecimal.ZERO,BigDecimal::add);
            Long itemId=insert("insert into sale_return_items(return_id,sale_item_id,quantity,restock,net_amount,tax_amount,total_amount,financial_cost) values(?,?,?,?,?,?,?,?)",id,plan.item().getId(),plan.quantity(),plan.restock(),plan.money().net(),plan.money().tax(),plan.money().payable(),lineCost);
            for(AllocationPlan allocation:plan.allocations()){
                StockBatch returnedBatch=plan.restock()?ledger.receiveReturn(plan.item().getProductCode(),allocation.quantity(),allocation.allocation().getUnitCost(),allocation.cost(),allocation.allocation().getBatch().getExpiryDate(),"คืนสินค้า "+number+" จาก "+original.getSaleNumber(),actor.storeId()):null;
                insert("insert into sale_return_allocations(return_item_id,sale_allocation_id,quantity,financial_cost,returned_batch_id) values(?,?,?,?,?)",itemId,allocation.allocation().getId(),allocation.quantity(),allocation.cost(),returnedBatch==null?null:returnedBatch.getId());
            }
        }
        for(var tender:payment.payments()){
            insert("insert into sale_refund_tenders(return_id,method,amount,reference) values(?,?,?,?)",id,tender.method().name(),tender.amount(),tender.reference());
            journals.post(actor.storeId(),"REFUND_"+tender.method().name(),id,number,"4010",tender.method().account(),tender.amount());
        }
        journals.post(actor.storeId(),"REFUND_VAT",id,number,"2200","4010",q.tax());
        journals.post(actor.storeId(),"RETURN_STOCK",id,number,"1200","5000",cost);
        audit.record(actor,"SALE_RETURN",number+" / "+original.getSaleNumber());return get(id,actor.storeId());
    }
    private Long insert(String sql,Object... args){var key=new GeneratedKeyHolder();jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    @Transactional(readOnly=true)
    public View get(Long id,Long storeId){return jdbc.query("select * from sale_returns where id=? and store_id=?",(r,n)->view(r),id,storeId).stream().findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบรายการคืนเงินในร้าน"));}
    @Transactional(readOnly=true)
    public List<View> list(Long saleId,Long storeId){sale(saleId,storeId);return jdbc.query("select * from sale_returns where sale_id=? and store_id=? order by id desc",(r,n)->view(r),saleId,storeId);}
    private View view(ResultSet r)throws SQLException{return new View(r.getLong("id"),r.getString("number"),r.getLong("sale_id"),r.getString("reason"),r.getTimestamp("created_at").toLocalDateTime(),r.getBigDecimal("net_amount"),r.getBigDecimal("tax_amount"),r.getBigDecimal("total_amount"),r.getBigDecimal("restocked_cost"));}
    private String fingerprint(AccessService.Actor actor,Long saleId,Input input,CheckoutTerms payment,String reason){
        if(input.lines()==null||input.lines().stream().anyMatch(l->l==null||l.itemId()==null))throw new IllegalArgumentException("ข้อมูลคืนสินค้าไม่ถูกต้อง");
        try{var buffer=new java.io.ByteArrayOutputStream();try(var data=new java.io.DataOutputStream(buffer)){
            data.writeLong(actor.userId());data.writeLong(saleId);data.writeUTF(reason);data.writeLong(input.revision());data.writeUTF(payment.expectedTotal().toPlainString());data.writeInt(input.lines().size());
            for(var line:input.lines().stream().sorted(Comparator.comparing(LineInput::itemId)).toList()){data.writeLong(line.itemId());data.writeInt(line.quantity());data.writeBoolean(line.restock());}
            for(var tender:payment.payments().stream().sorted(Comparator.comparing(p->p.method().name())).toList()){data.writeUTF(tender.method().name());data.writeUTF(tender.amount().toPlainString());data.writeUTF(tender.reference());}
        }return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()));}catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
