package com.kongklang.service;

import com.kongklang.entity.Product;
import com.kongklang.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KkAiServiceTest {
    ProductRepository products;
    OverviewService overview;
    KkAiService ai;
    Product water;
    @BeforeEach void setup() {
        products = mock(ProductRepository.class);
        overview = mock(OverviewService.class);
        ai = new KkAiService(mock(StoreAnalysisService.class), overview, products,
                mock(StockBatchRepository.class), mock(PurchaseOrderRepository.class));
        water = product("P001", "น้ำดื่ม", 12);
        when(products.findAllByStoreId(1L)).thenReturn(List.of(water));
        when(products.findByStoreIdAndCode(1L, "P001")).thenReturn(Optional.of(water));
    }
    Product product(String code, String name, int quantity) {
        Product p = new Product(); p.setCode(code); p.setName(name); p.setQuantity(quantity); p.setPrice(15.0); return p;
    }
    @ParameterizedTest @ValueSource(strings={"สินค้าในสต็อกมีอะไรบ้าง", "สินคาในสตอกมีอะไรบ้าง", "ในคลังมีอะไร", "ขอดู stock หน่อย"})
    void listsCurrentStoreStock(String q) {
        var reply = ai.answer(1L, q, null);
        assertEquals("สินค้าในร้าน", reply.title());
        assertTrue(reply.facts().getFirst().contains("P001"));
        verify(products, never()).findAll();
    }
    @ParameterizedTest @ValueSource(strings={"น้ำดื่มเหลือเท่าไหร่", "P001 เหลือกี่", "น้ำดืมเหลือเท่าไร", "น้ำดื่ม"})
    void findsNamedProductAndMinorTypo(String q) {
        var reply = ai.answer(1L, q, null);
        assertEquals("น้ำดื่ม", reply.title()); assertTrue(reply.text().contains("12"));
    }
    @Test void ambiguousNameAsksInsteadOfGuessing() {
        when(products.findAllByStoreId(1L)).thenReturn(List.of(product("A", "น้ำดื่มเล็ก", 2), product("B", "น้ำดื่มใหญ่", 3)));
        var reply = ai.answer(1L, "น้ำดื่มเหลือเท่าไหร่", null);
        assertEquals("หมายถึงสินค้าตัวไหนครับ?", reply.title()); assertEquals(2, reply.facts().size());
    }
    @Test void followupResolvesCurrentStoreOnly() {
        assertEquals("น้ำดื่ม", ai.answer(1L, "แล้วตัวนี้ราคาเท่าไหร่", null, "น้ำดื่มเหลือเท่าไหร่").title());
        var other = ai.answer(2L, "แล้วตัวนี้ราคาเท่าไหร่", null, "น้ำดื่มเหลือเท่าไหร่");
        assertEquals("ระบุสินค้า", other.title());
        verify(products, never()).findByStoreIdAndCode(2L, "P001");
    }
    @Test void explicitUnknownCodeDoesNotFallBackToAnotherProduct() {
        assertEquals("ค้นหาสินค้า", ai.answer(1L, "น้ำดื่มราคา", "P002").title());
    }
    @Test void writesAreNotPerformed() {
        assertEquals("ตรวจข้อมูลได้อย่างเดียว", ai.answer(1L, "ลบสินค้า P001", null).title());
        verifyNoInteractions(products);
    }
    @Test void emptyStoreHasNoInventedFacts() {
        var reply = ai.answer(2L, "สินค้าในสต็อกมีอะไรบ้าง", null);
        assertTrue(reply.facts().isEmpty()); assertTrue(reply.text().contains("ไม่พบ"));
    }
    @Test void unknownQuestionAdmitsLimitation() {
        assertNull(ai.answer(1L, "พรุ่งนี้ฝนตกไหม", null).sourcePath());
    }
    @Test void todayAndYesterdayUseCorrectBangkokDateWindow() {
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));
        when(overview.report(eq(1L), any(), any())).thenAnswer(call -> new OverviewService.Overview(
                call.getArgument(1), call.getArgument(2), 0, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0, List.of(), List.of()));
        ai.answer(1L, "ยอดข่ายวันนี้เท่าไหร่", null);
        verify(overview).report(1L, today, today);
        ai.answer(1L, "แล้วเมื่อวานล่ะ", null, "ยอดขายวันนี้");
        verify(overview).report(1L, today.minusDays(1), today.minusDays(1));
    }
    @Test void doesNotListWholeStoreForMissingNamedProduct() {
        assertEquals("ยังไม่พบสินค้าที่ตรงคำถาม", ai.answer(1L, "สินค้าแชมพูมีไหม", null).title());
    }
}
