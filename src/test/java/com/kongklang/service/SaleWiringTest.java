package com.kongklang.service;
import com.kongklang.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaleWiringTest {
    @Test void schemaGuardOverridesLegacyAutomaticUpdateSettings() {
        java.util.Map<String,Object> settings=new java.util.HashMap<>();settings.put("hibernate.hbm2ddl.auto","update");
        settings.put("jakarta.persistence.schema-generation.database.action","drop-and-create");
        new com.kongklang.config.SchemaValidationGuard().customize(settings);
        assertEquals("validate",settings.get("hibernate.hbm2ddl.auto"));
        assertFalse(settings.containsKey("jakarta.persistence.schema-generation.database.action"));
    }
    @Test void springCreatesRealSaleServiceUsingItsSingleConstructor() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(SaleRepository.class,()->mock(SaleRepository.class));
            context.registerBean(SaleItemRepository.class,()->mock(SaleItemRepository.class));
            context.registerBean(ProductRepository.class,()->mock(ProductRepository.class));
            context.registerBean(StoreRepository.class,()->mock(StoreRepository.class));
            context.registerBean(StockLedgerService.class,()->mock(StockLedgerService.class));
            context.registerBean(SaleAllocationRepository.class,()->mock(SaleAllocationRepository.class));
            context.registerBean(StockBatchRepository.class,()->mock(StockBatchRepository.class));
            context.registerBean(JournalService.class,()->mock(JournalService.class));
            context.registerBean(SaleService.class);context.refresh();
            assertNotNull(context.getBean(SaleService.class));assertFalse(mockingDetails(context.getBean(SaleService.class)).isMock());
        }
    }
}
