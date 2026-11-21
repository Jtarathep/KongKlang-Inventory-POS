package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Recognize a supplier liability from a posted manual receipt without receiving stock twice. */
@Service
@Transactional
public class ManualPayableService {
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}

    private final InventoryOperationRepository operations;
    private final SupplierRepository suppliers;
    private final ApInvoiceRepository invoices;
    private final JournalService journals;
    private final PayableService payables;
    public ManualPayableService(InventoryOperationRepository operations,SupplierRepository suppliers,
            ApInvoiceRepository invoices,JournalService journals,PayableService payables) {
        this.operations=operations;this.suppliers=suppliers;this.invoices=invoices;this.journals=journals;this.payables=payables;
    }
    public PayableService.InvoiceView invoice(AccessService.Actor actor,Input input) {
        if(actor==null||!java.util.Set.of("OWNER","ADMIN","ACCOUNTING").contains(actor.role()))throw new IllegalArgumentException("ไม่มีสิทธิ์ตั้งเจ้าหนี้");
        if(input==null||input.operationId()==null||input.supplierId()==null||input.invoiceDate()==null||input.dueDate()==null
                ||input.dueDate().isBefore(input.invoiceDate()))throw new IllegalArgumentException("กรุณาระบุเอกสาร ผู้จำหน่าย และวันที่ให้ครบ");
        String number=PurchasingService.text(input.vendorNumber(),100,"เลขใบแจ้งหนี้ผู้จำหน่าย");
        var operation=operations.lockByIdAndStoreId(input.operationId(),actor.storeId())
                .orElseThrow(()->new IllegalArgumentException("ไม่พบเอกสารรับสินค้าในร้าน"));
        if(!operation.getKind().equals("RECEIPT"))throw new IllegalArgumentException("ใช้ได้เฉพาะเอกสารรับเข้านอกใบสั่งซื้อ");
        var supplier=suppliers.findByIdAndStoreId(input.supplierId(),actor.storeId())
                .filter(s->Boolean.TRUE.equals(s.getActive())).orElseThrow(()->new IllegalArgumentException("ไม่พบผู้จำหน่ายที่เปิดใช้งานในร้าน"));
        var prior=invoices.findByInventoryOperationIdAndStoreId(operation.getId(),actor.storeId());
        if(prior.isPresent()) {
            var i=prior.get();
            if(!i.getSupplier().getId().equals(supplier.getId())||!i.getVendorNumber().equals(number)
                    ||!i.getInvoiceDate().equals(input.invoiceDate())||!i.getDueDate().equals(input.dueDate()))
                throw new IllegalArgumentException("เอกสารรับสินค้านี้ตั้งเจ้าหนี้แล้วด้วยข้อมูลอื่น");
            return payables.view(i);
        }
        if(invoices.existsByStoreIdAndSupplierIdAndVendorNumber(actor.storeId(),supplier.getId(),number))throw new IllegalArgumentException("เลขใบแจ้งหนี้ผู้จำหน่ายซ้ำ");
        journals.requireOpen(actor.storeId(),input.invoiceDate());
        var invoice=new ApInvoice();invoice.setStore(operation.getStore());invoice.setInventoryOperation(operation);invoice.setSupplier(supplier);
        invoice.setVendorNumber(number);invoice.setInvoiceDate(input.invoiceDate());invoice.setDueDate(input.dueDate());
        BigDecimal amount=operation.getValueAfter().subtract(operation.getValueBefore());
        if(amount.signum()<0)throw new IllegalStateException("มูลค่าเอกสารรับสินค้าไม่ถูกต้อง");
        invoice.setAmount(amount);invoice.setPaid(BigDecimal.ZERO);invoice.setStatus(amount.signum()==0?"PAID":"OPEN");invoice.setCreatedAt(PurchasingService.now());
        invoices.saveAndFlush(invoice);
        journals.post(actor.storeId(),"AP_INVOICE",invoice.getId(),number,"2110","2100",amount);
        if(audit!=null)audit.record(actor,"AP_MANUAL_INVOICE",number);
        return payables.view(invoice);
    }
    public record Input(Long operationId,Long supplierId,String vendorNumber,LocalDate invoiceDate,LocalDate dueDate){}
}
