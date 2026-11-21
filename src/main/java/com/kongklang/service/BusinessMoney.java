package com.kongklang.service;
import java.math.*;
public final class BusinessMoney {
    private BusinessMoney() {}
    public static BigDecimal cost(BigDecimal value) {
        if(value==null || value.signum()<0 || value.compareTo(new BigDecimal("999999999.9999"))>0)
            throw new IllegalArgumentException("ต้นทุนต้องอยู่ระหว่าง 0 ถึง 999999999.9999");
        return value.setScale(4,RoundingMode.UNNECESSARY);
    }
    public static BigDecimal amount(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.compareTo(new BigDecimal("999999999999.99"))>0)
            throw new IllegalArgumentException("จำนวนเงินต้องมากกว่า 0 และไม่เกิน 999999999999.99");
        return value.setScale(2,RoundingMode.UNNECESSARY);
    }
    public static BigDecimal line(BigDecimal cost,int quantity) {
        return cost.multiply(BigDecimal.valueOf(quantity)).setScale(2,RoundingMode.HALF_UP);
    }
}
