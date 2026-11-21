package com.kongklang.service;

import java.math.BigDecimal;
import java.util.*;

/** Amount is applied to the bill; received is physical cash before change. No gateway is called. */
public record CheckoutTerms(SalePricing.Discount discount, List<Payment> payments,
                            BigDecimal expectedTotal, boolean confirmed, Long taxRevision,
                            DocumentType documentType, Buyer buyer) {
    public enum DocumentType { RECEIPT, TAX_INVOICE }
    public record Buyer(String name,String address,String taxId,String branchCode) {
        public Buyer {
            name=clean(name,255);address=clean(address,1000);taxId=clean(taxId,13);branchCode=clean(branchCode,5);
            if(name.isBlank()||address.isBlank()||!taxId.matches("[0-9]{13}")||!branchCode.matches("[0-9]{5}"))
                throw new IllegalArgumentException("ข้อมูลผู้ซื้อสำหรับใบกำกับภาษีไม่ครบถ้วน");
        }
    }
    public CheckoutTerms(SalePricing.Discount discount,List<Payment> payments,BigDecimal expectedTotal,boolean confirmed){
        this(discount,payments,expectedTotal,confirmed,null,DocumentType.RECEIPT,null);
    }
    private static String clean(String value,int max){String v=value==null?"":value.trim();if(v.length()>max)throw new IllegalArgumentException("ข้อมูลเอกสารยาวเกินกำหนด");return v;}
    public enum Method {
        CASH("1100"), TRANSFER("1110"), CARD("1120");
        private final String account;
        Method(String account) { this.account = account; }
        public String account() { return account; }
    }
    public record Payment(Method method, BigDecimal amount, BigDecimal received, String reference) {
        public Payment {
            if (method == null) throw new IllegalArgumentException("กรุณาระบุวิธีชำระเงิน");
            amount = BusinessMoney.amount(amount);
            received = received == null ? amount : BusinessMoney.amount(received);
            if (received.compareTo(amount) < 0 || method != Method.CASH && received.compareTo(amount) != 0)
                throw new IllegalArgumentException("ยอดรับเงินจริงไม่ถูกต้อง");
            reference = reference == null ? "" : reference.trim();
            if (reference.length() > 100) throw new IllegalArgumentException("เลขอ้างอิงยาวเกิน 100 ตัวอักษร");
        }
        public BigDecimal change() { return received.subtract(amount); }
    }
    public CheckoutTerms {
        documentType=documentType==null?DocumentType.RECEIPT:documentType;
        if(documentType==DocumentType.TAX_INVOICE&&buyer==null)throw new IllegalArgumentException("กรุณาระบุข้อมูลผู้ซื้อใบกำกับภาษี");
        if(documentType==DocumentType.RECEIPT)buyer=null;
        discount = discount == null ? SalePricing.Discount.none() : discount;
        if (!confirmed) throw new IllegalArgumentException("กรุณายืนยันการรับเงินจริงก่อนบันทึก");
        if (expectedTotal == null || expectedTotal.signum() < 0)
            throw new IllegalArgumentException("กรุณาตรวจสอบยอดสุทธิก่อนชำระ");
        // Zero-value bills are allowed, but must have no invented tender.
        expectedTotal = expectedTotal.signum() == 0 ? new BigDecimal("0.00") : BusinessMoney.amount(expectedTotal);
        if (payments == null || payments.size() > 3) throw new IllegalArgumentException("วิธีชำระเงินไม่ถูกต้อง");
        Set<Method> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment == null || !seen.add(payment.method())) throw new IllegalArgumentException("วิธีชำระเงินซ้ำหรือไม่ถูกต้อง");
            total = total.add(payment.amount());
        }
        if (total.compareTo(expectedTotal) != 0) throw new IllegalArgumentException("ยอดชำระรวมต้องเท่ากับยอดสุทธิ");
        payments = List.copyOf(payments);
    }
    public String method() { return payments.isEmpty() ? "NONE" : payments.size() == 1 ? payments.getFirst().method().name() : "MIXED"; }
}
