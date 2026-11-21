package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayableServiceTest {
    GoodsReceiptRepository receipts;ApInvoiceRepository invoices;ApPaymentRepository payments;JournalService journals;PayableService service;
    ApInvoice invoice;AccessService.Actor actor=new AccessService.Actor(7L,1L,"ACCOUNTING");
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));
    @BeforeEach void setup() {
        receipts=mock(GoodsReceiptRepository.class);invoices=mock(ApInvoiceRepository.class);payments=mock(ApPaymentRepository.class);journals=mock(JournalService.class);
        service=new PayableService(receipts,invoices,payments,journals);
        Supplier supplier=new Supplier();supplier.setName("Supplier");supplier.setId(11L);
        GoodsReceipt receipt=new GoodsReceipt();receipt.setNumber("GR-TEST");receipt.setId(20L);receipt.setTotal(new BigDecimal("100.00"));
        PurchaseOrder po=new PurchaseOrder();po.setSupplier(supplier);receipt.setPurchaseOrder(po);
        invoice=new ApInvoice();invoice.setId(30L);invoice.setSupplier(supplier);invoice.setReceipt(receipt);invoice.setVendorNumber("INV1");
        invoice.setAmount(new BigDecimal("100.00"));invoice.setPaid(BigDecimal.ZERO);invoice.setInvoiceDate(today);invoice.setDueDate(today);invoice.setStatus("OPEN");
        when(invoices.lockByIdAndStoreId(30L,1L)).thenReturn(Optional.of(invoice));
        when(payments.saveAndFlush(any())).thenAnswer(inv->{ApPayment p=inv.getArgument(0);p.setId(40L);return p;});
        when(receipts.lockByIdAndStoreId(20L,1L)).thenReturn(Optional.of(receipt));
        when(invoices.saveAndFlush(any())).thenAnswer(inv->{ApInvoice i=inv.getArgument(0);i.setId(31L);return i;});
    }
    PayableService.PaymentInput pay(String key,String amount) {return new PayableService.PaymentInput(key,new BigDecimal(amount),today,"");}
    @Test void partialPaymentReducesBalanceAndPostsPayableDebit() {
        var result=service.pay(actor,30L,pay("p1","40.00"));assertEquals(new BigDecimal("60.00"),result.balance());assertEquals("PARTIALLY_PAID",result.status());
        verify(journals).post(eq(1L),eq("AP_PAYMENT"),eq(40L),anyString(),eq("2100"),eq("1100"),eq(new BigDecimal("40.00")));
    }
    @Test void finalPaymentClosesInvoice() {
        invoice.setPaid(new BigDecimal("40.00"));assertEquals("PAID",service.pay(actor,30L,pay("p1","60.00")).status());
    }
    @Test void overpaymentDoesNotSaveOrPost() {
        assertThrows(IllegalArgumentException.class,()->service.pay(actor,30L,pay("p1","100.01")));
        verify(payments,never()).saveAndFlush(any());verifyNoInteractions(journals);assertEquals(BigDecimal.ZERO,invoice.getPaid());
    }
    @Test void paymentRetryDoesNotReduceBalanceAgain() {
        service.pay(actor,30L,pay("p1","40.00"));var capture=org.mockito.ArgumentCaptor.forClass(ApPayment.class);verify(payments).saveAndFlush(capture.capture());
        when(payments.findByStoreIdAndRequestKey(1L,"p1")).thenReturn(Optional.of(capture.getValue()));clearInvocations(payments,journals);
        assertEquals(new BigDecimal("60.00"),service.pay(actor,30L,pay("p1","40.00")).balance());
        verify(payments,never()).saveAndFlush(any());verifyNoInteractions(journals);
    }
    @Test void paymentRetryWithDifferentAmountIsRejected() {
        service.pay(actor,30L,pay("p1","40.00"));var capture=org.mockito.ArgumentCaptor.forClass(ApPayment.class);verify(payments).saveAndFlush(capture.capture());
        when(payments.findByStoreIdAndRequestKey(1L,"p1")).thenReturn(Optional.of(capture.getValue()));
        assertThrows(IllegalArgumentException.class,()->service.pay(actor,30L,pay("p1","30.00")));
    }
    @Test void invoiceUsesReceivedValueAndTransfersAccruedLiability() {
        var result=service.invoice(actor,new PayableService.InvoiceInput(20L,"INV1",today,today));assertEquals(new BigDecimal("100.00"),result.amount());
        verify(journals).post(eq(1L),eq("AP_INVOICE"),eq(31L),eq("INV1"),eq("2110"),eq("2100"),eq(new BigDecimal("100.00")));
    }
    @Test void duplicateSupplierInvoiceNumberIsRejected() {
        when(invoices.existsByStoreIdAndSupplierIdAndVendorNumber(1L,11L,"INV1")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.invoice(actor,new PayableService.InvoiceInput(20L,"INV1",today,today)));
        verify(invoices,never()).saveAndFlush(any());verifyNoInteractions(journals);
    }
    @Test void crossStoreInvoiceCannotBePaid() {
        assertThrows(IllegalArgumentException.class,()->service.pay(new AccessService.Actor(8L,2L,"ACCOUNTING"),30L,pay("p1","1.00")));
        verify(payments,never()).saveAndFlush(any());verifyNoInteractions(journals);
    }
    @Test void fractionalSatangPaymentIsRejected() {
        assertThrows(ArithmeticException.class,()->service.pay(actor,30L,pay("p1","1.001")));
        verify(payments,never()).saveAndFlush(any());
    }
}
