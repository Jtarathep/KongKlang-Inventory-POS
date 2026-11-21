package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaleServiceTest {
    SaleRepository sales;SaleItemRepository items;ProductRepository products;StoreRepository stores;
    StockLedgerService ledger;SaleAllocationRepository allocations;StockBatchRepository batches;JournalService journals;SaleService service;
    AccessService.Actor actor=new AccessService.Actor(7L,1L,"ADMIN");Store store;Product product;
    @BeforeEach void setup() {
        sales=mock(SaleRepository.class);items=mock(SaleItemRepository.class);products=mock(ProductRepository.class);stores=mock(StoreRepository.class);
        ledger=mock(StockLedgerService.class);allocations=mock(SaleAllocationRepository.class);batches=mock(StockBatchRepository.class);journals=mock(JournalService.class);
        service=new SaleService(sales,items,products,stores,ledger,allocations,batches,journals);
        store=new Store();store.setId(1L);store.setActive(true);when(stores.findById(1L)).thenReturn(Optional.of(store));when(stores.lockById(1L)).thenReturn(Optional.of(store));
        product=new Product();product.setStore(store);product.setCode("P001");product.setName("Product");product.setPriceValue(new BigDecimal("0.10"));
        when(ledger.lockProduct(anyString(),eq(1L))).thenReturn(product);
        when(ledger.consume(eq("P001"),anyInt(),anyString(),eq(1L),eq(false))).thenAnswer(inv->{
            int quantity=inv.getArgument(1);BigDecimal cost=new BigDecimal("0.02").multiply(BigDecimal.valueOf(quantity));
            return new StockLedgerService.Consumption(10-quantity,cost,List.of(new StockLedgerService.Allocation(101L,quantity,new BigDecimal("0.02"),cost)));
        });
        StockBatch batch=new StockBatch();batch.setId(101L);when(batches.getReferenceById(101L)).thenReturn(batch);
        when(sales.saveAndFlush(any())).thenAnswer(inv->{Sale s=inv.getArgument(0);ReflectionTestUtils.setField(s,"id",10L);return s;});
        when(items.saveAndFlush(any())).thenAnswer(inv->{SaleItem i=inv.getArgument(0);ReflectionTestUtils.setField(i,"id",20L);return i;});
    }
    @Test void decimalSalePostsRevenueAndActualLotCostWithNoBinaryDrift() {
        Sale result=service.checkout("key",List.of("P001"),List.of(3),actor);
        assertEquals(new BigDecimal("0.30"),result.getTotalAmountValue());assertEquals(new BigDecimal("0.06"),result.getTotalCost());
        assertEquals("PAID",result.getPaymentStatus());assertEquals("CASH",result.getPaymentMethod());assertEquals(7L,result.getSoldBy().getId());
        verify(journals).post(eq(1L),eq("SALE_REVENUE"),eq(10L),anyString(),eq("1100"),eq("4000"),eq(new BigDecimal("0.30")));
        verify(journals).post(eq(1L),eq("SALE_COGS"),eq(10L),anyString(),eq("5000"),eq("1200"),eq(new BigDecimal("0.06")));
        var capture=org.mockito.ArgumentCaptor.forClass(SaleAllocation.class);verify(allocations).save(capture.capture());
        assertEquals(3,capture.getValue().getQuantity());assertEquals(0,new BigDecimal("0.06").compareTo(capture.getValue().getActualCost()));
    }
    @Test void discountedMixedSaleStoresTaxAndTenderSnapshotsAndPostsVatAsLiability(){
        product.setPriceValue(new BigDecimal("100.00"));store.setVatRegistered(true);store.setVatRate(new BigDecimal("7.00"));store.setTaxMode("EXCLUSIVE");
        when(stores.lockById(1L)).thenReturn(Optional.of(store));
        var terms=new CheckoutTerms(new SalePricing.Discount(SalePricing.DiscountType.PERCENT,new BigDecimal("10")),List.of(
            new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,new BigDecimal("40"),new BigDecimal("50"),""),
            new CheckoutTerms.Payment(CheckoutTerms.Method.TRANSFER,new BigDecimal("56.30"),null,"REF")),new BigDecimal("96.30"),true,0L,CheckoutTerms.DocumentType.RECEIPT,null);
        Sale result=service.checkout("mixed",List.of("P001"),List.of(1),actor,terms);
        assertEquals(new BigDecimal("90.00"),result.getNetAmount());assertEquals(new BigDecimal("6.30"),result.getTaxAmount());assertEquals(new BigDecimal("96.30"),result.getTotalAmountValue());
        assertEquals(new BigDecimal("10.00"),result.getDiscountAmount());assertEquals("MIXED",result.getPaymentMethod());assertEquals(2,result.getTenders().size());assertEquals(new BigDecimal("10.00"),result.getTenders().getFirst().getChangeAmount());
        verify(journals).post(eq(1L),eq("SALE_TRANSFER"),eq(10L),anyString(),eq("1110"),eq("4000"),eq(new BigDecimal("56.30")));
        verify(journals).post(eq(1L),eq("SALE_VAT"),eq(10L),anyString(),eq("4000"),eq("2200"),eq(new BigDecimal("6.30")));
        when(sales.findByStoreIdAndRequestKey(1L,"mixed")).thenReturn(Optional.of(result));store.setVatRate(new BigDecimal("9"));clearInvocations(ledger,journals);
        assertSame(result,service.checkout("mixed",List.of("P001"),List.of(1),actor,terms));verifyNoInteractions(ledger,journals);
    }
    @Test void staleTaxRevisionFailsBeforeStockChanges(){
        when(stores.lockById(1L)).thenReturn(Optional.of(store));
        var terms=new CheckoutTerms(null,List.of(new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,new BigDecimal("0.10"),null,"")),new BigDecimal("0.10"),true,2L,CheckoutTerms.DocumentType.RECEIPT,null);
        assertThrows(IllegalArgumentException.class,()->service.checkout("stale-tax",List.of("P001"),List.of(1),actor,terms));verifyNoInteractions(ledger,journals);
    }
    @Test void retrySameKeyReturnsOriginalDespiteLaterPriceChanges() {
        Sale first=service.checkout("key",List.of("P001"),List.of(3),actor);
        when(sales.findByStoreIdAndRequestKey(1L,"key")).thenReturn(Optional.of(first));product.setPriceValue(new BigDecimal("99.00"));
        clearInvocations(ledger,journals,allocations,sales);
        assertSame(first,service.checkout("key",List.of("P001"),List.of(3),actor));
        verifyNoInteractions(ledger,journals,allocations);verify(sales,never()).saveAndFlush(any());
    }
    @Test void changedPayloadWithSameKeyCannotSellAgain() {
        Sale first=service.checkout("key",List.of("P001"),List.of(3),actor);
        when(sales.findByStoreIdAndRequestKey(1L,"key")).thenReturn(Optional.of(first));clearInvocations(ledger,journals);
        assertThrows(IllegalArgumentException.class,()->service.checkout("key",List.of("P001"),List.of(4),actor));verifyNoInteractions(ledger,journals);
    }
    @Test void sameProductWithDifferentCaseIsMergedBeforeConsumption() {
        service.checkout("key",List.of("P001","p001"),List.of(1,2),actor);
        verify(ledger,times(1)).consume(eq("P001"),eq(3),anyString(),eq(1L),eq(false));
    }
    @Test void noSellerOrWarehouseRoleCannotCheckout() {
        assertThrows(IllegalArgumentException.class,()->service.checkout("key",List.of("P001"),List.of(1),null));
        assertThrows(IllegalArgumentException.class,()->service.checkout("key",List.of("P001"),List.of(1),new AccessService.Actor(7L,1L,"WAREHOUSE")));
        verifyNoInteractions(ledger,journals,sales);
    }
    @Test void missingPriceCannotBecomeAnAccidentalFreeSale() {
        product.setPriceValue(null);assertThrows(IllegalArgumentException.class,()->service.checkout("key",List.of("P001"),List.of(1),actor));
        verify(ledger,never()).consume(anyString(),anyInt(),anyString(),anyLong(),anyBoolean());verifyNoInteractions(journals);
    }
    @Test void allocationMismatchPreventsFinancialPosting() {
        when(ledger.consume(anyString(),anyInt(),anyString(),anyLong(),anyBoolean()))
            .thenReturn(new StockLedgerService.Consumption(0,BigDecimal.ONE,List.of()));
        assertThrows(IllegalStateException.class,()->service.checkout("key",List.of("P001"),List.of(1),actor));verifyNoInteractions(journals);
    }
    @Test void aCostPostingFailurePropagatesForTransactionRollback() {
        doThrow(new IllegalStateException("posting failed")).when(journals).post(eq(1L),eq("SALE_COGS"),anyLong(),anyString(),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->service.checkout("key",List.of("P001"),List.of(1),actor));
    }
    @Test void obsoleteUnscopedReadAndWriteMethodsFailClosed() {
        assertThrows(IllegalStateException.class,()->service.getAllSales());
        assertThrows(IllegalStateException.class,()->service.getTodayRevenue());
        assertThrows(IllegalStateException.class,()->service.createMultiSale("old",List.of("P001"),List.of(1),1L));
    }
    @Test void delimiterContainingCodesCannotCollideWithAnotherCart() {
        Map<String,Integer> first=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);first.put("A",1);first.put("B",2);
        Map<String,Integer> second=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);second.put("A:1|B",2);
        String firstHash=ReflectionTestUtils.invokeMethod(service,"fingerprint",7L,first);
        String secondHash=ReflectionTestUtils.invokeMethod(service,"fingerprint",7L,second);
        assertNotEquals(firstHash,secondHash);
    }
    @Test void saleUsesAllocatedBookValueRatherThanRoundingSubcentCostAgain() {
        var allocation=new StockLedgerService.Allocation(101L,1,new BigDecimal("0.0050"),new BigDecimal("0.0050"),new BigDecimal("0.00"));
        when(ledger.consume(anyString(),anyInt(),anyString(),anyLong(),anyBoolean()))
            .thenReturn(new StockLedgerService.Consumption(0,new BigDecimal("0.0050"),List.of(allocation),new BigDecimal("0.00")));
        Sale result=service.checkout("zero-book",List.of("P001"),List.of(1),actor);
        assertEquals(new BigDecimal("0.00"),result.getTotalCost());
        var capture=org.mockito.ArgumentCaptor.forClass(SaleItem.class);verify(items).saveAndFlush(capture.capture());
        assertEquals(new BigDecimal("0.0050"),capture.getValue().getActualCost());assertEquals(new BigDecimal("0.00"),capture.getValue().getCostValue());
    }
}
