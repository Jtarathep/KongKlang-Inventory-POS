package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class PurchasingService {
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final ProductRepository products;
    private final StockLedgerService ledger;
    private final JournalService journal;
    private final StoreRepository stores;

    public PurchasingService(SupplierRepository suppliers,PurchaseOrderRepository orders,GoodsReceiptRepository receipts,
            ProductRepository products,StockLedgerService ledger,JournalService journal,StoreRepository stores) {
        this.suppliers=suppliers; this.orders=orders; this.receipts=receipts;
        this.products=products; this.ledger=ledger; this.journal=journal;
        this.stores=stores;
    }

    public SupplierView saveSupplier(AccessService.Actor actor,Long id,SupplierInput input) {
        if(input==null) throw new IllegalArgumentException("กรุณาระบุข้อมูลผู้จำหน่าย");
        String code=text(input.code(),50,"รหัสผู้จำหน่าย");
        Supplier supplier=id==null?new Supplier():suppliers.findByIdAndStoreId(id,actor.storeId())
            .orElseThrow(()->new IllegalArgumentException("ไม่พบผู้จำหน่ายในร้านนี้"));
        if(id==null) {
            if(suppliers.existsByStoreIdAndCode(actor.storeId(),code)) throw new IllegalArgumentException("รหัสผู้จำหน่ายซ้ำในร้าน");
            supplier.setStore(store(actor.storeId())); supplier.setCode(code);
        } else if(!supplier.getCode().equals(code)) throw new IllegalArgumentException("ไม่สามารถเปลี่ยนรหัสผู้จำหน่าย");
        supplier.setName(text(input.name(),150,"ชื่อผู้จำหน่าย")); supplier.setPhone(optional(input.phone(),50));
        supplier.setEmail(optional(input.email(),150)); supplier.setContactName(optional(input.contactName(),150));
        supplier.setAddress(optional(input.address(),1000)); supplier.setActive(input.active()==null||input.active());
        return supplierView(suppliers.save(supplier));
    }
    @Transactional(readOnly=true)
    public List<SupplierView> suppliers(Long storeId) {
        return suppliers.findByStoreIdOrderByNameAsc(storeId).stream().map(this::supplierView).toList();
    }

    public OrderView createOrder(AccessService.Actor actor,OrderInput input) {
        if(input==null||input.items()==null||input.items().isEmpty()||input.items().size()>200)
            throw new IllegalArgumentException("กรุณาระบุสินค้า 1 ถึง 200 รายการ");
        if(input.supplierId()==null||input.items().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("กรุณาระบุผู้จำหน่ายและรายการสินค้า");
        String key=text(input.requestKey(),100,"รหัสป้องกันรายการซ้ำ");
        String fingerprint=orderFingerprint(input);
        stores.lockById(actor.storeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"));
        Optional<PurchaseOrder> prior=orders.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent()) {
            if(!prior.get().getRequestFingerprint().equals(fingerprint)) throw new IllegalArgumentException("รหัสใบสั่งซื้อซ้ำถูกใช้กับข้อมูลอื่นแล้ว");
            return orderView(prior.get());
        }
        Supplier supplier=suppliers.findByIdAndStoreId(input.supplierId(),actor.storeId())
            .filter(s->Boolean.TRUE.equals(s.getActive())).orElseThrow(()->new IllegalArgumentException("ไม่พบผู้จำหน่ายที่ใช้งานในร้าน"));
        PurchaseOrder po=new PurchaseOrder(); po.setStore(store(actor.storeId())); po.setSupplier(supplier);
        po.setOrderedBy(user(actor.userId())); po.setNumber(number("PO")); po.setStatus("ORDERED");
        po.setCreatedAt(now()); po.setNote(optional(input.note(),1000));
        po.setRequestKey(key);po.setRequestFingerprint(fingerprint);
        Set<String> codes=new HashSet<>(); BigDecimal total=BigDecimal.ZERO;
        for(OrderLine line:input.items()) {
            if(line==null||line.quantity()==null||line.quantity()<=0) throw new IllegalArgumentException("จำนวนสั่งซื้อต้องมากกว่า 0");
            String code=text(line.productCode(),255,"รหัสสินค้า");
            if(!codes.add(code.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("สินค้าในใบสั่งซื้อซ้ำ");
            Product product=products.findByStoreIdAndCode(actor.storeId(),code).orElseThrow(
                ()->new IllegalArgumentException("ไม่พบสินค้าในร้าน: "+code));
            PurchaseOrderItem item=new PurchaseOrderItem(); item.setPurchaseOrder(po); item.setProduct(product);
            item.setProductCode(product.getCode()); item.setProductName(product.getName()); item.setQuantity(line.quantity());
            item.setReceivedQuantity(0); item.setUnitCost(BusinessMoney.cost(line.unitCost())); po.getItems().add(item);
            total=total.add(BusinessMoney.line(item.getUnitCost(),line.quantity()));
        }
        if(total.compareTo(new BigDecimal("999999999999.99"))>0) throw new IllegalArgumentException("ยอดสั่งซื้อสูงเกินกำหนด");
        po.setTotal(total); PurchaseOrder saved=orders.saveAndFlush(po);
        if(audit!=null)audit.record(actor,"PURCHASE_ORDER",saved.getNumber());
        return orderView(saved);
    }
    @Transactional(readOnly=true)
    public List<OrderView> orders(Long storeId) {return orders.findByStoreIdOrderByCreatedAtDesc(storeId).stream().map(this::orderView).toList();}
    @Transactional(readOnly=true)
    public OrderView order(Long storeId,Long id) {return orderView(orders.findByIdAndStoreId(id,storeId).orElseThrow(()->new IllegalArgumentException("ไม่พบใบสั่งซื้อในร้าน")));}

    public OrderView cancel(AccessService.Actor actor,Long id) {
        PurchaseOrder po=orders.lockByIdAndStoreId(id,actor.storeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบใบสั่งซื้อในร้าน"));
        if(po.getStatus().equals("CANCELLED")) return orderView(po);
        if(!po.getStatus().equals("ORDERED") || po.getItems().stream().anyMatch(i->i.getReceivedQuantity()>0))
            throw new IllegalArgumentException("ยกเลิกได้เฉพาะใบสั่งซื้อที่ยังไม่รับสินค้า");
        po.setStatus("CANCELLED"); if(audit!=null)audit.record(actor,"PURCHASE_CANCEL",po.getNumber()); return orderView(po);
    }

    public ReceiptView receive(AccessService.Actor actor,Long orderId,ReceiptInput input) {
        if(input==null||input.items()==null||input.items().isEmpty()||input.items().size()>200)
            throw new IllegalArgumentException("กรุณาระบุรายการรับสินค้า");
        if(input.items().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("รายการรับสินค้าไม่ถูกต้อง");
        String key=text(input.requestKey(),100,"รหัสป้องกันรายการซ้ำ");
        String fingerprint=fingerprint(orderId,input.items());
        PurchaseOrder po=orders.lockByIdAndStoreId(orderId,actor.storeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบใบสั่งซื้อในร้าน"));
        Optional<GoodsReceipt> prior=receipts.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent()) {
            if(!prior.get().getRequestFingerprint().equals(fingerprint)) throw new IllegalArgumentException("รหัสรายการซ้ำถูกใช้กับข้อมูลอื่นแล้ว");
            return receiptView(prior.get());
        }
        if(!Set.of("ORDERED","PARTIALLY_RECEIVED").contains(po.getStatus())) throw new IllegalArgumentException("สถานะใบสั่งซื้อไม่สามารถรับสินค้าได้");
        Map<Long,PurchaseOrderItem> byId=new HashMap<>(); for(PurchaseOrderItem i:po.getItems()) byId.put(i.getId(),i);
        Set<Long> seen=new HashSet<>();
        for(ReceiptLine line:input.items()) {
            if(line==null||line.orderItemId()==null||!seen.add(line.orderItemId())) throw new IllegalArgumentException("รายการรับสินค้าซ้ำหรือไม่ถูกต้อง");
            PurchaseOrderItem ordered=byId.get(line.orderItemId());
            if(ordered==null) throw new IllegalArgumentException("รายการสินค้าไม่อยู่ในใบสั่งซื้อนี้");
            if(line.quantity()==null||line.quantity()<=0||line.quantity()>ordered.getQuantity()-ordered.getReceivedQuantity())
                throw new IllegalArgumentException("จำนวนรับสินค้าต้องมากกว่า 0 และไม่เกินยอดค้างรับ");
        }
        GoodsReceipt gr=new GoodsReceipt(); gr.setStore(store(actor.storeId())); gr.setPurchaseOrder(po);
        gr.setReceivedBy(user(actor.userId())); gr.setNumber(number("GR")); gr.setRequestKey(key);
        gr.setRequestFingerprint(fingerprint); gr.setReceivedAt(now());
        BigDecimal total=BigDecimal.ZERO;
        List<ReceiptLine> sorted=input.items().stream().sorted(Comparator.comparing(l->byId.get(l.orderItemId()).getProductCode())).toList();
        for(ReceiptLine line:sorted) {
            PurchaseOrderItem ordered=byId.get(line.orderItemId());
            int before=ordered.getReceivedQuantity(), after=Math.addExact(before,line.quantity());
            BigDecimal lineValue=BusinessMoney.line(ordered.getUnitCost(),after).subtract(BusinessMoney.line(ordered.getUnitCost(),before));
            StockBatch batch=ledger.receiveBatchValue(ordered.getProductCode(),line.quantity(),ordered.getUnitCost(),null,
                    line.expiryDate(),"รับสินค้าตาม "+gr.getNumber()+" / "+po.getNumber(),actor.storeId(),lineValue);
            GoodsReceiptItem received=new GoodsReceiptItem(); received.setReceipt(gr); received.setOrderItem(ordered);
            received.setStockBatch(batch); received.setQuantity(line.quantity()); received.setUnitCost(ordered.getUnitCost());
            received.setExpiryDate(line.expiryDate()); gr.getItems().add(received);
            // Incremental rounding ensures all partial receipts sum exactly to the PO line amount.
            total=total.add(lineValue);
            ordered.setReceivedQuantity(after);
        }
        gr.setTotal(total); receipts.saveAndFlush(gr);
        po.setStatus(po.getItems().stream().allMatch(i->i.getReceivedQuantity().equals(i.getQuantity()))?"RECEIVED":"PARTIALLY_RECEIVED");
        journal.post(actor.storeId(),"GOODS_RECEIPT",gr.getId(),gr.getNumber(),"1200","2110",total);
        if(audit!=null)audit.record(actor,"GOODS_RECEIPT",gr.getNumber());
        return receiptView(gr);
    }
    @Transactional(readOnly=true)
    public List<ReceiptView> receipts(Long storeId) {return receipts.findByStoreIdOrderByReceivedAtDesc(storeId).stream().map(this::receiptView).toList();}

    private String fingerprint(Long orderId,List<ReceiptLine> lines) {
        try {
            String canonical=orderId+"|"+lines.stream().sorted(Comparator.comparing(ReceiptLine::orderItemId,Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(l->l.orderItemId()+":"+l.quantity()+":"+l.expiryDate()).reduce("",(a,b)->a+"|"+b);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private String orderFingerprint(OrderInput input) {
        String canonical=input.supplierId()+"|"+optional(input.note(),1000)+"|"+input.items().stream()
            .map(l->text(l.productCode(),255,"รหัสสินค้า").toUpperCase(Locale.ROOT)+":"+l.quantity()+":"+BusinessMoney.cost(l.unitCost()).toPlainString())
            .sorted().reduce("",(a,b)->a+"|"+b);
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private SupplierView supplierView(Supplier s) {return new SupplierView(s.getId(),s.getCode(),s.getName(),s.getContactName(),s.getPhone(),s.getEmail(),s.getAddress(),s.getActive());}
    private OrderView orderView(PurchaseOrder po) {return new OrderView(po.getId(),po.getNumber(),po.getSupplier().getId(),po.getSupplier().getName(),po.getStatus(),po.getTotal(),po.getCreatedAt(),po.getNote(),po.getItems().stream().map(i->new OrderItemView(i.getId(),i.getProductCode(),i.getProductName(),i.getQuantity(),i.getReceivedQuantity(),i.getUnitCost())).toList());}
    private ReceiptView receiptView(GoodsReceipt gr) {return new ReceiptView(gr.getId(),gr.getNumber(),gr.getPurchaseOrder().getId(),gr.getPurchaseOrder().getNumber(),gr.getTotal(),gr.getReceivedAt(),gr.getItems().stream().map(i->new ReceiptItemView(i.getOrderItem().getProductCode(),i.getQuantity(),i.getUnitCost(),i.getExpiryDate(),i.getStockBatch().getId())).toList());}
    static Store store(Long id) {Store s=new Store();s.setId(id);return s;}
    static UserAccount user(Long id) {UserAccount u=new UserAccount();u.setId(id);return u;}
    static LocalDateTime now() {return LocalDateTime.now(ZoneId.of("Asia/Bangkok"));}
    static String number(String prefix) {return prefix+"-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);}
    static String text(String value,int max,String label) {if(value==null||value.isBlank()||value.trim().length()>max)throw new IllegalArgumentException("กรุณาระบุ"+label+"ไม่เกิน "+max+" ตัวอักษร");return value.trim();}
    static String optional(String value,int max) {if(value!=null&&value.length()>max)throw new IllegalArgumentException("ข้อความยาวเกิน "+max+" ตัวอักษร");return value==null?"":value.trim();}
    public record SupplierInput(String code,String name,String contactName,String phone,String email,String address,Boolean active) {}
    public record SupplierView(Long id,String code,String name,String contactName,String phone,String email,String address,Boolean active) {}
    public record OrderLine(String productCode,Integer quantity,BigDecimal unitCost) {}
    public record OrderInput(String requestKey,Long supplierId,String note,List<OrderLine> items) {}
    public record OrderItemView(Long id,String productCode,String productName,Integer quantity,Integer receivedQuantity,BigDecimal unitCost) {}
    public record OrderView(Long id,String number,Long supplierId,String supplierName,String status,BigDecimal total,LocalDateTime createdAt,String note,List<OrderItemView> items) {}
    public record ReceiptLine(Long orderItemId,Integer quantity,LocalDate expiryDate) {}
    public record ReceiptInput(String requestKey,List<ReceiptLine> items) {}
    public record ReceiptItemView(String productCode,Integer quantity,BigDecimal unitCost,LocalDate expiryDate,Long stockBatchId) {}
    public record ReceiptView(Long id,String number,Long orderId,String orderNumber,BigDecimal total,LocalDateTime receivedAt,List<ReceiptItemView> items) {}
}
