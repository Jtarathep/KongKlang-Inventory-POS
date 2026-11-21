package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockLedgerServiceTest {
    ProductRepository products;
    StockBatchRepository batches;
    StockTransactionRepository transactions;
    StockLedgerService ledger;
    Product product;
    List<StockBatch> stock;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));

    @BeforeEach void setup() {
        products = mock(ProductRepository.class);
        batches = mock(StockBatchRepository.class);
        transactions = mock(StockTransactionRepository.class);
        ledger = new StockLedgerService(products, batches, transactions);
        Store store = new Store(); store.setId(1L);
        product = new Product(); product.setStore(store); product.setCode("P001");
        product.setName("สินค้า"); product.setQuantity(0); product.setCostPrice(2.0);
        stock = new ArrayList<>();
        when(products.findForUpdateByStoreIdAndCode(1L, "P001")).thenReturn(Optional.of(product));
        when(batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(1L, "P001")).thenReturn(stock);
    }

    StockBatch batch(long id, int quantity, double cost, LocalDate expiry) {
        StockBatch b = new StockBatch(); b.setId(id); b.setQuantity(quantity);
        b.setRemainingQuantity(quantity); b.setPurchaseCost(cost); b.setExpiryDate(expiry);
        b.setReceivedAt(today.atStartOfDay().minusDays(id)); b.setStore(product.getStore());
        b.setReceivedValue(com.kongklang.support.DecimalMoney.cents(BigDecimal.valueOf(cost).multiply(BigDecimal.valueOf(quantity))));
        b.setRemainingValue(b.getReceivedValue());
        b.setProductCode("P001"); b.setProductName("สินค้า"); stock.add(b);
        product.setQuantity(Math.addExact(product.getQuantity(), quantity));
        return b;
    }

    @Test void saleSplitsAcrossBatchesByExpiryAndSavesActualCost() {
        StockBatch later = batch(1, 10, 2.0, today.plusDays(10));
        StockBatch first = batch(2, 3, 1.5, today.plusDays(2));
        StockBatch noExpiry = batch(3, 4, 3.0, null);
        var result = ledger.consume("P001", 5, "SALE", 1L, false);
        assertEquals(0, first.getRemainingQuantity());
        assertEquals(8, later.getRemainingQuantity());
        assertEquals(4, noExpiry.getRemainingQuantity());
        assertEquals(12, result.balance());
        assertEquals(0, new BigDecimal("8.5").compareTo(result.cost()));
        ArgumentCaptor<StockTransaction> tx = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactions).save(tx.capture());
        assertEquals("OUT", tx.getValue().getType());
        assertEquals(12, tx.getValue().getBalance());
        assertSame(product.getStore(), tx.getValue().getStore());
    }

    @Test void expiredLotsCannotCoverASaleAndFailureDoesNotMutateBatches() {
        StockBatch expired = batch(1, 8, 2, today.minusDays(1));
        StockBatch valid = batch(2, 2, 3, today.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> ledger.consume("P001", 3, "SALE", 1L, false));
        assertEquals(8, expired.getRemainingQuantity()); assertEquals(2, valid.getRemainingQuantity());
        assertEquals(10, product.getQuantity());
        verify(batches, never()).save(any()); verify(transactions, never()).save(any());
    }

    @Test void expiryTodayIsStillSellable() {
        batch(1, 1, 2, today);
        assertEquals(0, ledger.consume("P001", 1, "SALE", 1L, false).balance());
    }

    @Test void receiptCreatesANewBatchAndHistory() {
        batch(1, 2, 2, null);
        ledger.receive("P001", 4, 2.5, 5.0, today.plusDays(5), "รับเข้า", 1L);
        assertEquals(6, product.getQuantity());
        assertEquals(2.5, product.getCostPrice()); assertEquals(5.0, product.getPrice());
        ArgumentCaptor<StockBatch> received = ArgumentCaptor.forClass(StockBatch.class);
        verify(batches).save(received.capture());
        assertEquals(4, received.getValue().getRemainingQuantity());
        assertNull(received.getValue().getId());
        assertSame(product.getStore(), received.getValue().getStore());
        verify(transactions).save(any());
    }

    @Test void downwardCountAdjustsLotsIncludingExpiredStock() {
        StockBatch expired = batch(1, 3, 2, today.minusDays(1));
        StockBatch valid = batch(2, 5, 3, today.plusDays(1));
        ledger.adjust("P001", 4, "สินค้าชำรุด", 1L);
        assertEquals(0, expired.getRemainingQuantity()); assertEquals(4, valid.getRemainingQuantity());
        assertEquals(4, product.getQuantity());
        ArgumentCaptor<StockTransaction> tx = ArgumentCaptor.forClass(StockTransaction.class);
        verify(transactions).save(tx.capture());
        assertEquals(-4, tx.getValue().getQuantity()); assertEquals("ADJUST", tx.getValue().getType());
    }

    @Test void upwardCountCreatesANoExpiryBatchAtRecordedCost() {
        batch(1, 2, 2, today.plusDays(1));
        ledger.adjust("P001", 5, "ตรวจนับ", 1L);
        ArgumentCaptor<StockBatch> added = ArgumentCaptor.forClass(StockBatch.class);
        verify(batches).save(added.capture());
        assertEquals(3, added.getValue().getQuantity());
        assertNull(added.getValue().getExpiryDate()); assertEquals(5, product.getQuantity());
    }

    @Test void mismatchedLegacyBalanceBlocksMutationInsteadOfInventingStock() {
        batch(1, 2, 2, null); product.setQuantity(5);
        assertThrows(IllegalStateException.class, () -> ledger.consume("P001", 1, "SALE", 1L, false));
        assertThrows(IllegalStateException.class, () -> ledger.receive("P001", 1, 2.0, null, null, "IN", 1L));
        assertThrows(IllegalStateException.class, () -> ledger.adjust("P001", 2, "count", 1L));
        verify(batches, never()).save(any()); verify(transactions, never()).save(any());
    }

    @Test void rejectsUnknownStoreProductWithoutCrossStoreFallback() {
        when(products.findForUpdateByStoreIdAndCode(2L, "P001")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> ledger.consume("P001", 1, "SALE", 2L, false));
        verifyNoInteractions(batches, transactions);
        verify(products, never()).findByStoreIdAndCode(any(), any());
    }

    @Test void rejectsMissingStoreBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> ledger.consume("P001", 1, "SALE", null, false));
        verifyNoInteractions(products, batches, transactions);
    }

    @Test void rejectsNonFiniteAndMissingReceiptCosts() {
        for (Double cost : Arrays.asList(Double.NaN, Double.POSITIVE_INFINITY, -1.0, null))
            assertThrows(IllegalArgumentException.class, () -> ledger.receive("P001", 1, cost, null, null, "IN", 1L));
        verify(batches, never()).save(any());
    }

    @Test void quantityOverflowDoesNotSaveAnything() {
        batch(1, Integer.MAX_VALUE, 1, null);
        assertThrows(ArithmeticException.class, () -> ledger.receive("P001", 1, 1.0, null, null, "IN", 1L));
        verify(batches, never()).save(any());
    }

    @Test void rejectsMissingAdjustmentReason() {
        assertThrows(IllegalArgumentException.class, () -> ledger.adjust("P001", 1, " ", 1L));
        verifyNoInteractions(products, batches, transactions);
    }

    @Test void zeroChangeCountDoesNotCreateHistory() {
        batch(1, 2, 2, null);
        ledger.adjust("P001", 2, "ตรวจนับ", 1L);
        verify(transactions, never()).save(any()); verify(batches, never()).save(any());
    }

    @Test void rejectsCorruptNegativeLotBalance() {
        StockBatch bad = batch(1, 2, 2, null); bad.setRemainingQuantity(-1);
        assertThrows(IllegalStateException.class, () -> ledger.consume("P001", 1, "SALE", 1L, false));
        verify(batches, never()).save(any());
    }
    @Test void splittingSubcentStockAcrossSalesNeverCreatesMoreBookCostThanReceivedValue() {
        StockBatch b=batch(1,2,0.005,null);
        var first=ledger.consume("P001",1,"sale1",1L,false);
        var second=ledger.consume("P001",1,"sale2",1L,false);
        assertEquals(new BigDecimal("0.01"),first.bookCost().add(second.bookCost()));
        assertEquals(BigDecimal.ZERO.setScale(2),b.getRemainingValue());
        assertEquals(0,new BigDecimal("0.010").compareTo(first.cost().add(second.cost())));
    }
    @Test void receiptRoundingOverrideIsPreservedInTheLotAndFutureCost() {
        when(batches.save(any())).thenAnswer(inv->{StockBatch b=inv.getArgument(0);stock.add(b);return b;});
        StockBatch b=ledger.receiveBatchValue("P001",1,new BigDecimal("0.0050"),null,null,"partial",1L,new BigDecimal("0.00"));
        assertEquals(new BigDecimal("0.00"),b.getReceivedValue());
        var used=ledger.consume("P001",1,"sale",1L,false);
        assertEquals(new BigDecimal("0.00"),used.bookCost());assertEquals(0,new BigDecimal("0.0050").compareTo(used.cost()));
    }
}
