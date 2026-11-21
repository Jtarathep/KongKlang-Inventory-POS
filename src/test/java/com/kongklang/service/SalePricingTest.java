package com.kongklang.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static com.kongklang.service.SalePricing.*;
import static org.junit.jupiter.api.Assertions.*;

class SalePricingTest {
    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private Quote price(String amount, TaxMode mode, Discount discount) {
        return quote(List.of(new Input("A", 1, b(amount))), new TaxRule(mode, b("7")), discount);
    }

    @Test void unregisteredShopDoesNotChargeOrExtractVat() {
        Quote q = quote(List.of(new Input("A", 1, b("100"))), TaxRule.none(), Discount.none());
        assertEquals(b("100.00"), q.net()); assertEquals(b("0.00"), q.tax()); assertEquals(b("100.00"), q.payable());
    }

    @Test void inclusiveHundredKeepsTheCustomersPrice() {
        Quote q = price("100.00", TaxMode.INCLUSIVE, Discount.none());
        assertEquals(b("93.46"), q.net()); assertEquals(b("6.54"), q.tax()); assertEquals(b("100.00"), q.payable());
    }
    @Test void exclusiveHundredAddsSevenBaht() {
        Quote q = price("100.00", TaxMode.EXCLUSIVE, Discount.none());
        assertEquals(b("100.00"), q.net()); assertEquals(b("7.00"), q.tax()); assertEquals(b("107.00"), q.payable());
    }
    @Test void discountReducesTheTaxBaseBeforeVatIsCalculated() {
        Quote q = price("100.00", TaxMode.EXCLUSIVE, new Discount(DiscountType.PERCENT, b("10")));
        assertEquals(b("10.00"), q.discount()); assertEquals(b("90.00"), q.net());
        assertEquals(b("6.30"), q.tax()); assertEquals(b("96.30"), q.payable());
        Quote included = price("107.00", TaxMode.INCLUSIVE, new Discount(DiscountType.AMOUNT, b("10")));
        assertEquals(b("90.65"), included.net()); assertEquals(b("6.35"), included.tax()); assertEquals(b("97.00"), included.payable());
    }
    @Test void roundAtBillLevelAndAllocateRemainingSatangDeterministically() {
        Quote q = quote(List.of(new Input("A", 1, b("0.05")), new Input("B", 1, b("0.05"))),
                new TaxRule(TaxMode.INCLUSIVE, b("7")), Discount.none());
        assertEquals(b("0.01"), q.tax()); assertEquals(b("0.01"), q.lines().getFirst().tax());
        assertEquals(b("0.00"), q.lines().getLast().tax()); assertEquals(b("0.09"), q.net());
    }
    @Test void freeLinesAndFullDiscountDoNotDivideByZero() {
        Quote q = quote(List.of(new Input("A", 3, b("0"))), TaxRule.none(), Discount.none());
        assertEquals(b("0.00"), q.payable());
        assertEquals(b("0.00"), price("100", TaxMode.EXCLUSIVE, new Discount(DiscountType.PERCENT, b("100"))).payable());
    }
    @Test void rejectsNegativeOverDiscountPrecisionAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new Input("A", 0, b("1")));
        assertThrows(IllegalArgumentException.class, () -> new Input("A", 1, b("-1")));
        assertThrows(IllegalArgumentException.class, () -> new Input("A", 1, b("1.001")));
        assertThrows(IllegalArgumentException.class, () -> new TaxRule(TaxMode.NONE, b("7")));
        assertThrows(IllegalArgumentException.class, () -> new Discount(DiscountType.PERCENT, b("101")));
        assertThrows(IllegalArgumentException.class, () -> price("1", TaxMode.EXCLUSIVE, new Discount(DiscountType.AMOUNT, b("2"))));
        assertThrows(IllegalArgumentException.class, () -> quote(List.of(new Input("A", Integer.MAX_VALUE, b("999999999999.99"))), TaxRule.none(), Discount.none()));
        assertThrows(IllegalArgumentException.class, () -> price("999999999999.99", TaxMode.EXCLUSIVE, Discount.none()));
    }
    @Test void partialRefundsUseOriginalSnapshotsAndNeverExceedTheLine() {
        Line line = quote(List.of(new Input("A", 3, b("0.05"))), new TaxRule(TaxMode.INCLUSIVE, b("7")),
                new Discount(DiscountType.AMOUNT, b("0.01"))).lines().getFirst();
        BigDecimal total = b("0.00"), net = b("0.00"), vat = b("0.00"), discount = b("0.00");
        for (int i = 0; i < 3; i++) {
            Refund r = refund(line, i, 1);
            total = total.add(r.payable()); net = net.add(r.net()); vat = vat.add(r.tax()); discount = discount.add(r.discount());
        }
        assertEquals(line.payable(), total); assertEquals(line.net(), net); assertEquals(line.tax(), vat); assertEquals(line.discount(), discount);
        assertEquals(line.payable(), refund(line, 0, 3).payable());
        assertThrows(IllegalArgumentException.class, () -> refund(line, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> refund(line, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    @Test void generatedBasketsConserveEverySatangAcrossLinesAndPartialRefunds() {
        Random random = new Random(813);
        for (TaxMode mode : TaxMode.values()) for (int attempt = 0; attempt < 250; attempt++) {
            List<Input> basket = new ArrayList<>();
            for (int i = 0; i < 1 + random.nextInt(20); i++)
                basket.add(new Input("P" + i, 1 + random.nextInt(10), BigDecimal.valueOf(random.nextInt(50000), 2)));
            Quote q = quote(basket, new TaxRule(mode, mode == TaxMode.NONE ? b("0") : b("7")),
                    new Discount(DiscountType.PERCENT, BigDecimal.valueOf(random.nextInt(10001), 2)));
            assertEquals(q.payable(), q.net().add(q.tax()));
            assertEquals(q.listedAmount().subtract(q.discount()), mode == TaxMode.INCLUSIVE ? q.payable() : q.net());
            assertEquals(q.discount(), q.lines().stream().map(Line::discount).reduce(b("0.00"), BigDecimal::add));
            assertEquals(q.net(), q.lines().stream().map(Line::net).reduce(b("0.00"), BigDecimal::add));
            assertEquals(q.tax(), q.lines().stream().map(Line::tax).reduce(b("0.00"), BigDecimal::add));
            for (Line line : q.lines()) {
                BigDecimal refunded = b("0.00");
                for (int i = 0; i < line.quantity(); i++) refunded = refunded.add(refund(line, i, 1).payable());
                assertEquals(line.payable(), refunded);
            }
        }
    }
    @Test void snapshotDoesNotExposeMutableLineCollection() {
        Quote q = price("100", TaxMode.EXCLUSIVE, Discount.none());
        assertThrows(UnsupportedOperationException.class, () -> q.lines().clear());
    }
}
