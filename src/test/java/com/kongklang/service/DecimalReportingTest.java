package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import com.kongklang.support.DecimalMoney;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecimalReportingTest {
    @Test void profitReportAggregatesTenthsExactlyAndOnlyFromRequestedStore() {
        SaleItemRepository items=mock(SaleItemRepository.class);Sale sale=new Sale();ReflectionTestUtils.setField(sale,"id",1L);
        ReflectionTestUtils.setField(sale,"createdAt",LocalDate.of(2026,11,12).atTime(12,0));
        List<SaleItem> rows=new ArrayList<>();
        for(String value:List.of("0.10","0.20")) {
            SaleItem item=new SaleItem();item.setSale(sale);item.setProductCode("P001");item.setQuantity(1);item.setSubtotalValue(new BigDecimal(value));item.setCostValue(new BigDecimal("0.01"));rows.add(item);
        }
        when(items.findAllBySaleStoreId(2L)).thenReturn(rows);
        var result=new ProfitReportService(items).generateReport(LocalDate.of(2026,11,12),LocalDate.of(2026,11,12),2L);
        assertEquals(new BigDecimal("0.30"),result.getTotalSalesValue());assertEquals(new BigDecimal("0.28"),result.getTotalProfitValue());
        assertEquals(new BigDecimal("0.30"),result.getProducts().getFirst().getSalesValue());verify(items,never()).findAll();
    }
    @Test void reportWithoutStoreCannotFallBackToEveryStore() {
        SaleItemRepository items=mock(SaleItemRepository.class);
        assertThrows(IllegalStateException.class,()->new ProfitReportService(items).generateReport(LocalDate.now(),LocalDate.now()));verifyNoInteractions(items);
    }
    @Test void decimalEntityBoundariesRejectNaNAndFractionalSatangPrices() {
        Product p=new Product();assertThrows(IllegalArgumentException.class,()->p.setPrice(Double.NaN));
        assertThrows(ArithmeticException.class,()->p.setPriceValue(new BigDecimal("1.001")));
        p.setCostPriceValue(new BigDecimal("1.2345"));assertEquals(new BigDecimal("1.2345"),p.getCostPriceValue());
    }
    @Test void explicitCostRoundingIsHalfUpAtFinancialBoundary() {
        assertEquals(new BigDecimal("0.01"),DecimalMoney.cents(new BigDecimal("0.0050")));
        assertEquals(new BigDecimal("0.00"),DecimalMoney.cents(new BigDecimal("0.0049")));
    }
}
