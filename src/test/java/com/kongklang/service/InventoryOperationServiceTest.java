package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryOperationServiceTest {
    Product product;List<StockBatch> lots;Map<String,InventoryOperation> saved;
    InventoryOperationRepository operations;ProductRepository products;StockBatchRepository batches;
    StockTransactionRepository transactions;StockLedgerService ledger;JournalService journals;InventoryOperationService service;
    AccessService.Actor actor=new AccessService.Actor(7L,1L,"WAREHOUSE");
    @BeforeEach void setup() {
        operations=mock(InventoryOperationRepository.class);products=mock(ProductRepository.class);batches=mock(StockBatchRepository.class);
        transactions=mock(StockTransactionRepository.class);journals=mock(JournalService.class);lots=new ArrayList<>();saved=new HashMap<>();
        Store store=new Store();store.setId(1L);product=new Product();product.setId(5L);product.setStore(store);product.setCode("P001");product.setName("Demo");
        product.setVersion(0L);product.setQuantity(0);product.setCostPriceValue(new BigDecimal("2.5000"));
        when(products.findForUpdateByStoreIdAndCode(1L,"P001")).thenReturn(Optional.of(product));
        when(batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(1L,"P001")).thenAnswer(i->new ArrayList<>(lots));
        when(batches.save(any())).thenAnswer(i->{StockBatch b=i.getArgument(0);if(b.getId()==null){b.setId(100L+lots.size());lots.add(b);}return b;});
        when(operations.findByStoreIdAndRequestKey(eq(1L),anyString())).thenAnswer(i->Optional.ofNullable(saved.get(i.getArgument(1))));
        when(operations.saveAndFlush(any())).thenAnswer(i->{InventoryOperation o=i.getArgument(0);if(o.getId()==null)o.setId(20L+saved.size());saved.put(o.getRequestKey(),o);return o;});
        ledger=new StockLedgerService(products,batches,transactions);service=new InventoryOperationService(operations,ledger,journals);
    }
    InventoryOperationService.Input input(String key,String kind,int qty,Integer expected,Long version,String cost) {
        return new InventoryOperationService.Input(key,kind,"P001",qty,expected,version,cost==null?null:new BigDecimal(cost),null,null,"ตรวจนับหรือเอกสารอ้างอิง");
    }
    void seed(int qty,String cost){ledger.receiveBatchValue("P001",qty,new BigDecimal(cost),null,null,"fixture",1L);}
    @Test void receiptCreatesAuditedLotsAndClearingJournalWithoutInventingCashPayment() {
        var result=service.execute(actor,input("r1","RECEIPT",3,null,null,"2.5000"));
        assertEquals(3,product.getQuantity());assertEquals(new BigDecimal("7.50"),result.valueAfter());
        assertEquals(7L,result.performedBy());assertEquals(1,result.lots().size());assertEquals(3,result.lots().getFirst().quantityChange());
        verify(journals).post(1L,"INVENTORY_RECEIPT",20L,result.number(),"1200","2110",new BigDecimal("7.50"));
    }
    @Test void retryReturnsOriginalBeforeAndAfterEvenWhenCurrentCostChanges() {
        var request=input("r1","RECEIPT",1,null,null,null);var first=service.execute(actor,request);
        product.setCostPriceValue(new BigDecimal("99.0000"));var replay=service.execute(actor,request);
        assertEquals(first,replay);assertEquals(1,product.getQuantity());assertEquals(1,lots.size());
        verify(journals,times(1)).post(anyLong(),anyString(),anyLong(),anyString(),anyString(),anyString(),any());
    }
    @Test void changedPayloadOrActorCannotReuseRequestKey() {
        service.execute(actor,input("r1","RECEIPT",1,null,null,"2.5"));
        assertThrows(IllegalArgumentException.class,()->service.execute(actor,input("r1","RECEIPT",2,null,null,"2.5")));
        assertThrows(IllegalArgumentException.class,()->service.execute(new AccessService.Actor(8L,1L,"WAREHOUSE"),input("r1","RECEIPT",1,null,null,"2.5")));
        assertEquals(1,product.getQuantity());
    }
    @Test void physicalWriteOffCanRemoveExpiredLotsAndPostsLossRatherThanSales() {
        seed(3,"2.5000");lots.getFirst().setExpiryDate(LocalDate.of(2000,1,1));
        var out=service.execute(actor,input("out","OUT",2,null,null,null));
        assertEquals(1,product.getQuantity());assertEquals(-2,out.lots().getFirst().quantityChange());assertEquals(new BigDecimal("-5.00"),out.lots().getFirst().valueChange());
        verify(journals).post(1L,"INVENTORY_OUT",20L,out.number(),"5100","1200",new BigDecimal("5.00"));
    }
    @Test void countGainCreatesNewLotAndGainJournal() {
        seed(2,"2.5000");var result=service.execute(actor,input("count","ADJUST",5,2,0L,null));
        assertEquals(2,lots.size());assertEquals(new BigDecimal("12.50"),result.valueAfter());
        verify(journals).post(1L,"INVENTORY_ADJUST",20L,result.number(),"1200","4100",new BigDecimal("7.50"));
    }
    @Test void countLossUsesRecordedBookValueAndPreservesRemainingLotValue() {
        seed(2,"0.0050");var result=service.execute(actor,input("count","ADJUST",1,2,0L,null));
        assertEquals(new BigDecimal("0.00"),result.valueAfter());assertEquals(new BigDecimal("-0.01"),result.lots().getFirst().valueChange());
        verify(journals).post(1L,"INVENTORY_ADJUST",20L,result.number(),"5100","1200",new BigDecimal("0.01"));
    }
    @Test void splitWriteOffsCannotRoundTotalExpenseAboveLotValue() {
        seed(2,"0.0050");var first=service.execute(actor,input("o1","OUT",1,null,null,null));
        var second=service.execute(actor,input("o2","OUT",1,null,null,null));
        assertEquals(new BigDecimal("0.01"),first.valueBefore().subtract(first.valueAfter()).add(second.valueBefore().subtract(second.valueAfter())));
        assertEquals(new BigDecimal("0.00"),lots.getFirst().getRemainingValue());
    }
    @Test void changedQuantityRejectsStaleCountBeforeWritingOperation() {
        seed(3,"2.5000");assertThrows(IllegalArgumentException.class,()->service.execute(actor,input("count","ADJUST",1,2,0L,null)));
        assertEquals(3,product.getQuantity());verify(operations,never()).saveAndFlush(any());verifyNoInteractions(journals);
    }
    @Test void changedVersionRejectsCountEvenWhenQuantityReturnedToOriginalValue() {
        seed(2,"2.5000");product.setVersion(2L);
        assertThrows(IllegalArgumentException.class,()->service.execute(actor,input("count","ADJUST",1,2,0L,null)));
        assertEquals(2,product.getQuantity());verify(operations,never()).saveAndFlush(any());
    }
    @Test void unchangedCountAndZeroValueReceiptsStillHaveAuditWithoutInventedMoney() {
        var zero=service.execute(actor,input("free","RECEIPT",1,null,null,"0"));
        var count=service.execute(actor,input("count","ADJUST",1,1,0L,null));
        assertEquals(0,zero.valueAfter().signum());assertEquals(0,count.lots().size());assertEquals(2,saved.size());
    }
    @Test void onlyInternalNewProductOpeningCanPostOpeningEquity() {
        var opening=service.opening(new AccessService.Actor(7L,1L,"ADMIN"),"P001",2,new BigDecimal("2.5000"),5L);
        verify(journals).post(1L,"INVENTORY_OPENING",20L,opening.number(),"1200","3100",new BigDecimal("5.00"));
        assertThrows(IllegalArgumentException.class,()->service.execute(actor,input("illegal","OPENING",2,0,null,"2.5")));
        assertThrows(IllegalArgumentException.class,()->service.opening(new AccessService.Actor(7L,1L,"ADMIN"),"P001",2,new BigDecimal("2.5000"),6L));
    }
    @Test void missingCountVersionAndUnauthorizedRolesDoNotMutateStock() {
        assertThrows(IllegalArgumentException.class,()->service.execute(actor,input("count","ADJUST",1,0,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.execute(new AccessService.Actor(7L,1L,"SALES"),input("r1","RECEIPT",1,null,null,"2.5")));
        verifyNoInteractions(operations,products,journals);
    }
    @Test void journalFailurePropagatesSoTransactionBoundaryCanRollBack() {
        doThrow(new IllegalStateException("journal failed")).when(journals).post(anyLong(),anyString(),anyLong(),anyString(),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->service.execute(actor,input("r1","RECEIPT",1,null,null,"2.5")));
        // Mock repositories do not implement rollback; live rollback is covered by the opt-in MySQL suite.
    }
    @Test void otherStoreCannotResolveProduct() {
        assertThrows(IllegalArgumentException.class,()->service.execute(new AccessService.Actor(7L,2L,"ADMIN"),input("r1","RECEIPT",1,null,null,"2.5")));
        verify(operations,never()).saveAndFlush(any());verifyNoInteractions(journals);
    }
    @Test void longReasonIsPreservedInAuditAndReferencedByStockHistory() {
        String reason="เหตุผลทดสอบ".repeat(80);
        var request=new InventoryOperationService.Input("long","RECEIPT","P001",1,null,null,BigDecimal.ONE,null,null,reason);
        var result=service.execute(actor,request);assertEquals(reason,result.note());
        var capture=org.mockito.ArgumentCaptor.forClass(StockTransaction.class);verify(transactions).save(capture.capture());
        assertTrue(capture.getValue().getNote().contains(reason));assertTrue(capture.getValue().getNote().startsWith(result.number()));
        assertTrue(capture.getValue().getNote().length()<=1200);
    }

}
