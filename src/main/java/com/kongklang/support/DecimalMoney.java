package com.kongklang.support;
import java.math.*;

public final class DecimalMoney {
    private DecimalMoney() {}
    public static BigDecimal normalize(BigDecimal value,int scale) {
        if(value==null) return null;
        if(value.signum()<0 || value.precision()-value.scale()>15)
            throw new IllegalArgumentException("จำนวนเงินต้องไม่ติดลบและไม่เกินขนาดที่ระบบรองรับ");
        return value.setScale(scale,RoundingMode.UNNECESSARY);
    }
    public static BigDecimal legacy(Double value,int scale) {
        if(value==null) return null;
        if(!Double.isFinite(value)) throw new IllegalArgumentException("จำนวนเงินต้องเป็นตัวเลขที่มีค่าจำกัด");
        return normalize(BigDecimal.valueOf(value),scale);
    }
    public static BigDecimal required(BigDecimal value,String label) {
        if(value==null) throw new IllegalArgumentException("กรุณาระบุ"+label);
        return value;
    }
    public static BigDecimal zero(BigDecimal value) {return value==null?BigDecimal.ZERO:value;}
    public static BigDecimal cents(BigDecimal value) {return value.setScale(2,RoundingMode.HALF_UP);}
}
