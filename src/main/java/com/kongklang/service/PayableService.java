package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class PayableService {
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}

    private final GoodsReceiptRepository receipts;
    private final ApInvoiceRepository invoices;
    private final ApPaymentRepository payments;
    private final JournalService journal;
    public PayableService(GoodsReceiptRepository receipts,ApInvoiceRepository invoices,ApPaymentRepository payments,JournalService journal) {
        this.receipts=receipts;this.invoices=invoices;this.payments=payments;this.journal=journal;
    }
    public InvoiceView invoice(AccessService.Actor actor,InvoiceInput input) {
        if(input==null||input.receiptId()==null||input.invoiceDate()==null||input.dueDate()==null||input.dueDate().isBefore(input.invoiceDate()))
            throw new IllegalArgumentException("กรุณาระบุเอกสารรับสินค้าและวันที่ใบแจ้งหนี้/ครบกำหนดให้ถูกต้อง");
        String vendorNumber=PurchasingService.text(input.vendorNumber(),100,"เลขใบแจ้งหนี้ผู้จำหน่าย");
        GoodsReceipt receipt=receipts.lockByIdAndStoreId(input.receiptId(),actor.storeId())
            .orElseThrow(()->new IllegalArgumentException("ไม่พบเอกสารรับสินค้าในร้าน"));
        Optional<ApInvoice> prior=invoices.findByReceiptIdAndStoreId(receipt.getId(),actor.storeId());
        if(prior.isPresent()) {
            ApInvoice invoice=prior.get();
            if(!invoice.getVendorNumber().equals(vendorNumber)||!invoice.getInvoiceDate().equals(input.invoiceDate())||!invoice.getDueDate().equals(input.dueDate()))
                throw new IllegalArgumentException("เอกสารรับสินค้านี้มีใบแจ้งหนี้แล้ว");
            return view(invoice);
        }
        Supplier supplier=receipt.getPurchaseOrder().getSupplier();
        if(invoices.existsByStoreIdAndSupplierIdAndVendorNumber(actor.storeId(),supplier.getId(),vendorNumber))
            throw new IllegalArgumentException("เลขใบแจ้งหนี้ผู้จำหน่ายซ้ำ");
        journal.requireOpen(actor.storeId(),input.invoiceDate());
        ApInvoice invoice=new ApInvoice(); invoice.setStore(PurchasingService.store(actor.storeId()));
        invoice.setReceipt(receipt);invoice.setSupplier(supplier);invoice.setVendorNumber(vendorNumber);
        invoice.setInvoiceDate(input.invoiceDate());invoice.setDueDate(input.dueDate());invoice.setAmount(receipt.getTotal());
        invoice.setPaid(BigDecimal.ZERO);invoice.setStatus(receipt.getTotal().signum()==0?"PAID":"OPEN");invoice.setCreatedAt(PurchasingService.now());
        invoices.saveAndFlush(invoice);
        journal.post(actor.storeId(),"AP_INVOICE",invoice.getId(),vendorNumber,"2110","2100",invoice.getAmount());
        if(audit!=null)audit.record(actor,"AP_INVOICE",vendorNumber);
        return view(invoice);
    }
    public InvoiceView pay(AccessService.Actor actor,Long invoiceId,PaymentInput input) {
        if(input==null||input.paymentDate()==null||input.paymentDate().isAfter(LocalDate.now(ZoneId.of("Asia/Bangkok"))))
            throw new IllegalArgumentException("กรุณาระบุวันที่ชำระเงินที่ไม่เป็นวันในอนาคต");
        BigDecimal amount=BusinessMoney.amount(input.amount());
        String key=PurchasingService.text(input.requestKey(),100,"รหัสป้องกันรายการซ้ำ");
        String note=PurchasingService.optional(input.note(),1000);
        ApInvoice invoice=invoices.lockByIdAndStoreId(invoiceId,actor.storeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบใบแจ้งหนี้ในร้าน"));
        Optional<ApPayment> prior=payments.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent()) {
            ApPayment payment=prior.get();
            if(!payment.getInvoice().getId().equals(invoiceId)||payment.getAmount().compareTo(amount)!=0
                    ||!payment.getPaymentDate().equals(input.paymentDate())||!Objects.equals(payment.getNote(),note))
                throw new IllegalArgumentException("รหัสชำระเงินซ้ำถูกใช้กับข้อมูลอื่นแล้ว");
            return view(invoice);
        }
        if(input.paymentDate().isBefore(invoice.getInvoiceDate())) throw new IllegalArgumentException("วันที่ชำระเงินต้องไม่ก่อนวันที่ใบแจ้งหนี้");
        BigDecimal balance=invoice.getAmount().subtract(invoice.getPaid());
        if(amount.compareTo(balance)>0) throw new IllegalArgumentException("ยอดชำระเกินยอดค้างชำระ");
        journal.requireOpen(actor.storeId(),input.paymentDate());
        ApPayment payment=new ApPayment();payment.setStore(PurchasingService.store(actor.storeId()));payment.setInvoice(invoice);
        payment.setPaidBy(PurchasingService.user(actor.userId()));payment.setRequestKey(key);payment.setNumber(PurchasingService.number("PAY"));
        payment.setAmount(amount);payment.setPaymentDate(input.paymentDate());payment.setNote(note);payment.setCreatedAt(PurchasingService.now());
        payments.saveAndFlush(payment);
        invoice.setPaid(invoice.getPaid().add(amount));invoice.setStatus(invoice.getPaid().compareTo(invoice.getAmount())==0?"PAID":"PARTIALLY_PAID");
        journal.post(actor.storeId(),"AP_PAYMENT",payment.getId(),payment.getNumber(),"2100","1100",amount);
        if(audit!=null)audit.record(actor,"AP_PAYMENT",payment.getNumber());
        return view(invoice);
    }
    @Transactional(readOnly=true)
    public List<InvoiceView> list(Long storeId) {return invoices.findByStoreIdOrderByCreatedAtDesc(storeId).stream().map(this::view).toList();}
    InvoiceView view(ApInvoice i) {return new InvoiceView(i.getId(),i.getVendorNumber(),i.getReceipt()!=null?i.getReceipt().getNumber():i.getInventoryOperation().getNumber(),
        i.getSupplier().getName(),i.getInvoiceDate(),i.getDueDate(),i.getAmount(),i.getPaid(),i.getAmount().subtract(i.getPaid()),i.getStatus());}
    public record InvoiceInput(Long receiptId,String vendorNumber,LocalDate invoiceDate,LocalDate dueDate) {}
    public record PaymentInput(String requestKey,BigDecimal amount,LocalDate paymentDate,String note) {}
    public record InvoiceView(Long id,String vendorNumber,String receiptNumber,String supplierName,LocalDate invoiceDate,LocalDate dueDate,BigDecimal amount,BigDecimal paid,BigDecimal balance,String status) {}
}
