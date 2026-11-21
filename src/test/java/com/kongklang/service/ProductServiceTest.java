package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    ProductRepository products;
    StockBatchRepository batches;
    StockTransactionRepository transactions;
    StockLedgerService ledger;
    InventoryOperationService operations; AccessService access;
    ProductService service;
    HttpSession session;
    Product product;

    @BeforeEach void setup() {
        products = mock(ProductRepository.class); batches = mock(StockBatchRepository.class);
        transactions = mock(StockTransactionRepository.class); ledger = mock(StockLedgerService.class);
        StoreContextService context = mock(StoreContextService.class);
        CategoryService categories = mock(CategoryService.class);
        session = mock(HttpSession.class);
        when(context.getCurrentStoreId(session)).thenReturn(1L);
        when(categories.existsByNameForStore("food", session)).thenReturn(true);
        operations=mock(InventoryOperationService.class);access=mock(AccessService.class);
        when(access.require(session,"OWNER","ADMIN")).thenReturn(new AccessService.Actor(7L,1L,"ADMIN"));
        service = new ProductService(products, context, categories, operations, access, batches, transactions);
        Store store = new Store(); store.setId(1L);
        product = new Product(); product.setCode("P001"); product.setCategory("food");
        product.setName("สินค้า"); product.setQuantity(0); product.setPrice(5.0); product.setStore(store);
    }

    @Test void duplicateCreateCannotSilentlyIncreaseExistingStock() {
        when(products.existsByStoreIdAndCode(1L, "P001")).thenReturn(true);
        product.setQuantity(10);
        assertThrows(IllegalArgumentException.class, () -> service.saveProduct(product, session));
        verify(products, never()).saveAndFlush(any()); verifyNoInteractions(ledger);
    }

    @Test void openingBalanceUsesLedgerInsteadOfDirectQuantityWrite() {
        product.setQuantity(5); product.setCostPrice(2.5);
        service.saveProduct(product, session);
        assertEquals(0, product.getQuantity());
        verify(products).saveAndFlush(product);
        verify(operations).opening(any(),eq("P001"),eq(5),eq(new java.math.BigDecimal("2.5000")),isNull());
    }

    @Test void metadataEditCannotChangeStock() {
        Product saved = new Product(); saved.setCode("P001"); saved.setQuantity(5);
        when(products.findForUpdateByIdAndStoreId(7L, 1L)).thenReturn(Optional.of(saved));
        product.setQuantity(6);
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(7L, product, session));
        assertEquals(5, saved.getQuantity()); verify(products, never()).save(any());
    }

    @Test void metadataEditCannotRenameCodeAndDisconnectHistoricalLots() {
        Product saved = new Product(); saved.setCode("OLD"); saved.setQuantity(0);
        when(products.findForUpdateByIdAndStoreId(7L, 1L)).thenReturn(Optional.of(saved));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(7L, product, session));
        assertEquals("OLD", saved.getCode()); verify(products, never()).save(any());
    }

    @Test void cannotDeleteProductWithMovementHistoryEvenAtZeroBalance() {
        when(products.findForUpdateByIdAndStoreId(7L, 1L)).thenReturn(Optional.of(product));
        when(batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(1L, "P001")).thenReturn(List.of());
        when(transactions.existsByStoreIdAndProductCode(1L, "P001")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.deleteProduct(7L, session));
        verify(products, never()).delete(any());
    }
    @Test void inventoryValueUsesRemainingLotBookValueInsteadOfRetailPrice() {
        StockBatch first=new StockBatch();first.setRemainingValue(new java.math.BigDecimal("0.10"));
        StockBatch second=new StockBatch();second.setRemainingValue(new java.math.BigDecimal("0.20"));
        when(batches.findAllByStoreId(1L)).thenReturn(List.of(first,second));
        assertEquals(0.30,service.getInventoryValue(session));verify(products,never()).findAllByStoreId(anyLong());
    }
}
