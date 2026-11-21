package com.kongklang.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure, immutable sale snapshots. All allocation takes place in integer satang. */
public final class SalePricing {
    private SalePricing() {}
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal LIMIT = new BigDecimal("999999999999.99");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public enum TaxMode { NONE, INCLUSIVE, EXCLUSIVE }
    public enum DiscountType { NONE, AMOUNT, PERCENT }

    public record TaxRule(TaxMode mode, BigDecimal rate) {
        public TaxRule {
            if (mode == null) throw invalid("กรุณาระบุวิธีคิดภาษี");
            rate = percentage(rate);
            if (mode == TaxMode.NONE && rate.signum() != 0)
                throw invalid("เมื่อไม่คิดภาษี อัตราต้องเป็นศูนย์");
        }
        public static TaxRule none() { return new TaxRule(TaxMode.NONE, ZERO); }
    }

    /** Discount is expressed in the same basis as the entered price, before tax extraction/addition. */
    public record Discount(DiscountType type, BigDecimal value) {
        public Discount {
            if (type == null) throw invalid("กรุณาระบุประเภทส่วนลด");
            value = type == DiscountType.PERCENT ? percentage(value) : money(value);
            if (type == DiscountType.NONE && value.signum() != 0)
                throw invalid("ส่วนลดที่ไม่ใช้งานต้องเป็นศูนย์");
        }
        public static Discount none() { return new Discount(DiscountType.NONE, ZERO); }
    }

    public record Input(String code, int quantity, BigDecimal unitPrice) {
        public Input {
            if (code == null || code.isBlank() || code.length() > 255 || quantity <= 0)
                throw invalid("รหัสสินค้าหรือจำนวนไม่ถูกต้อง");
            code = code.trim();
            unitPrice = money(unitPrice);
        }
    }

    public record Line(String code, int quantity, BigDecimal unitPrice, BigDecimal listedAmount,
                       BigDecimal discount, BigDecimal net, BigDecimal tax, BigDecimal payable) {}
    public record Quote(TaxRule rule, Discount discountRule, List<Line> lines, BigDecimal listedAmount,
                        BigDecimal discount, BigDecimal net, BigDecimal tax, BigDecimal payable) {
        public Quote { lines = List.copyOf(lines); }
    }
    public record Refund(BigDecimal discount, BigDecimal net, BigDecimal tax, BigDecimal payable) {}

    public static Quote quote(List<Input> input, TaxRule tax, Discount discount) {
        if (input == null || input.isEmpty() || input.size() > 200 || tax == null || discount == null)
            throw invalid("กรุณาระบุสินค้า 1 ถึง 200 รายการและกฎราคา");
        List<BigDecimal> amounts = new ArrayList<>();
        for (Input line : input) {
            if (line == null) throw invalid("รายการสินค้าไม่ถูกต้อง");
            amounts.add(money(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()))));
        }
        BigDecimal listed = money(sum(amounts));
        BigDecimal reduction = switch (discount.type()) {
            case NONE -> ZERO;
            case AMOUNT -> discount.value();
            case PERCENT -> listed.multiply(discount.value()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        };
        if (reduction.compareTo(listed) > 0) throw invalid("ส่วนลดเกินมูลค่าสินค้า");
        List<BigDecimal> reductions = allocate(reduction, amounts);
        List<BigDecimal> after = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) after.add(amounts.get(i).subtract(reductions.get(i)));
        BigDecimal discounted = listed.subtract(reduction);
        BigDecimal vat = switch (tax.mode()) {
            case NONE -> ZERO;
            case INCLUSIVE -> discounted.multiply(tax.rate()).divide(HUNDRED.add(tax.rate()), 2, RoundingMode.HALF_UP);
            case EXCLUSIVE -> discounted.multiply(tax.rate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        };
        List<BigDecimal> taxes = allocate(vat, after);
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            BigDecimal net = tax.mode() == TaxMode.INCLUSIVE ? after.get(i).subtract(taxes.get(i)) : after.get(i);
            Input item = input.get(i);
            lines.add(new Line(item.code(), item.quantity(), item.unitPrice(), amounts.get(i), reductions.get(i),
                    net, taxes.get(i), money(net.add(taxes.get(i)))));
        }
        BigDecimal net = sum(lines.stream().map(Line::net).toList());
        return new Quote(tax, discount, lines, listed, reduction, net, vat, money(net.add(vat)));
    }

    /** Cumulative rounding ensures any sequence of partial refunds exhausts the snapshot exactly once. */
    public static Refund refund(Line original, int previouslyReturned, int quantity) {
        if (original == null || original.quantity() <= 0 || previouslyReturned < 0 || quantity <= 0
                || previouslyReturned > original.quantity() || quantity > original.quantity() - previouslyReturned)
            throw invalid("จำนวนคืนเกินจำนวนที่เหลือในบิล");
        int end = previouslyReturned + quantity;
        BigDecimal discount = slice(original.discount(), original.quantity(), previouslyReturned, end);
        BigDecimal net = slice(original.net(), original.quantity(), previouslyReturned, end);
        BigDecimal vat = slice(original.tax(), original.quantity(), previouslyReturned, end);
        return new Refund(discount, net, vat, net.add(vat));
    }

    private static BigDecimal slice(BigDecimal amount, int total, int start, int end) {
        money(amount);
        return amount.multiply(BigDecimal.valueOf(end)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .subtract(amount.multiply(BigDecimal.valueOf(start)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
    }

    /** Largest remainder; ties follow the original stable line order. No binary floating point. */
    private static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        BigInteger denominator = sum(weights).movePointRight(2).toBigIntegerExact();
        BigInteger cents = total.movePointRight(2).toBigIntegerExact();
        List<BigInteger> shares = new ArrayList<>();
        List<BigInteger> remainders = new ArrayList<>();
        BigInteger allocated = BigInteger.ZERO;
        for (BigDecimal weight : weights) {
            BigInteger[] result = denominator.signum() == 0 ? new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO}
                    : cents.multiply(weight.movePointRight(2).toBigIntegerExact()).divideAndRemainder(denominator);
            shares.add(result[0]); remainders.add(result[1]); allocated = allocated.add(result[0]);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) order.add(i);
        order.sort(Comparator.<Integer, BigInteger>comparing(remainders::get).reversed());
        int remaining = cents.subtract(allocated).intValueExact();
        if (remaining > order.size()) throw new IllegalStateException("ไม่สามารถกระจายยอดได้");
        for (int i = 0; i < remaining; i++) {
            int index = order.get(i); shares.set(index, shares.get(index).add(BigInteger.ONE));
        }
        return shares.stream().map(value -> new BigDecimal(value, 2)).toList();
    }

    private static BigDecimal sum(List<BigDecimal> values) { return values.stream().reduce(ZERO, BigDecimal::add); }
    private static BigDecimal percentage(BigDecimal value) {
        BigDecimal result = money(value);
        if (result.compareTo(HUNDRED) > 0) throw invalid("อัตราต้องอยู่ระหว่าง 0 ถึง 100");
        return result;
    }
    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(LIMIT) > 0)
            throw invalid("จำนวนเงินไม่ถูกต้องหรือเกินขอบเขต");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw invalid("จำนวนเงินต้องไม่เกินสองตำแหน่งทศนิยม"); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
