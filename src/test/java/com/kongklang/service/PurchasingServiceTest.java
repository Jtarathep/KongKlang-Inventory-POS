package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchasingServiceTest {
    SupplierRepository suppliers; PurchaseOrderRepository orders; GoodsReceiptRepository receipts;
    ProductRepository products; StockLedgerService ledger; JournalService journal; StoreRepository stores; PurchasingService service;
    AccessService.Actor actor=new AccessService.Actor(7L,1L,"ADMIN");
    Supplier supplier; PurchaseOrder order; PurchaseOrderItem line;
    @BeforeEach void setup() {
        suppliers=mock(SupplierRepository.class);orders=mock(PurchaseOrderRepository.class);receipts=mock(GoodsReceiptRepository.class);
        products=mock(ProductRepository.class);ledger=mock(StockLedgerService.class);journal=mock(JournalService.class);
        stores=mock(StoreRepository.class);
        Store lockedStore=new Store();lockedStore.setId(1L);when(stores.lockById(anyLong())).thenReturn(Optional.of(lockedStore));
        service=new PurchasingService(suppliers,orders,receipts,products,ledger,journal,stores);
        Store store=new Store();store.setId(1L); supplier=new Supplier();supplier.setId(11L);supplier.setStore(store);supplier.setName("Supplier");supplier.setActive(true);
        Product product=new Product();product.setCode("P001");product.setName("Product");product.setStore(store);
        when(suppliers.findByIdAndStoreId(11L,1L)).thenReturn(Optional.of(supplier));
        when(products.findByStoreIdAndCode(1L,"P001")).thenReturn(Optional.of(product));
        order=new PurchaseOrder();order.setId(20L);order.setNumber("PO-TEST");order.setStore(store);order.setSupplier(supplier);order.setStatus("ORDERED");
        line=new PurchaseOrderItem();line.setId(30L);line.setProduct(product);line.setPurchaseOrder(order);line.setProductCode("P001");line.setProductName("Product");
        line.setQuantity(5);line.setReceivedQuantity(0);line.setUnitCost(new BigDecimal("2.5555"));order.getItems().add(line);
        order.setTotal(new BigDecimal("12.78"));
        when(orders.lockByIdAndStoreId(20L,1L)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any())).thenAnswer(inv->{PurchaseOrder p=inv.getArgument(0);p.setId(21L);return p;});
        when(receipts.saveAndFlush(any())).thenAnswer(inv->{GoodsReceipt r=inv.getArgument(0);r.setId(40L);return r;});
        when(ledger.receiveBatchValue(anyString(),anyInt(),any(BigDecimal.class),isNull(),any(),anyString(),eq(1L),any(BigDecimal.class)))
            .thenAnswer(inv->{StockBatch b=new StockBatch();b.setId(50L);return b;});
    }
    PurchasingService.ReceiptInput receipt(String key,int quantity) {return new PurchasingService.ReceiptInput(key,List.of(new PurchasingService.ReceiptLine(30L,quantity,null)));}
    @Test void purchaseOrderDoesNotIncreaseStockOrPostAccounts() {
        var result=service.createOrder(actor,new PurchasingService.OrderInput("po-key",11L,"",List.of(new PurchasingService.OrderLine("P001",3,new BigDecimal("2.5555")))));
        assertEquals(new BigDecimal("7.67"),result.total());assertEquals("ORDERED",result.status());
        verifyNoInteractions(ledger,journal);
    }
    @Test void repeatedOrderKeyReturnsSameDocumentAfterLostResponse() {
        var input=new PurchasingService.OrderInput("po-key",11L,"",List.of(new PurchasingService.OrderLine("P001",3,new BigDecimal("2.5555"))));
        var first=service.createOrder(actor,input);
        var capture=org.mockito.ArgumentCaptor.forClass(PurchaseOrder.class);verify(orders).saveAndFlush(capture.capture());
        when(orders.findByStoreIdAndRequestKey(1L,"po-key")).thenReturn(Optional.of(capture.getValue()));clearInvocations(orders);
        assertEquals(first.id(),service.createOrder(actor,input).id());verify(orders,never()).saveAndFlush(any());
    }
    @Test void changedOrderPayloadWithSameKeyIsRejected() {
        service.createOrder(actor,new PurchasingService.OrderInput("po-key",11L,"",List.of(new PurchasingService.OrderLine("P001",3,BigDecimal.ONE))));
        var capture=org.mockito.ArgumentCaptor.forClass(PurchaseOrder.class);verify(orders).saveAndFlush(capture.capture());
        when(orders.findByStoreIdAndRequestKey(1L,"po-key")).thenReturn(Optional.of(capture.getValue()));
        assertThrows(IllegalArgumentException.class,()->service.createOrder(actor,new PurchasingService.OrderInput("po-key",11L,"",List.of(new PurchasingService.OrderLine("P001",4,BigDecimal.ONE)))));
    }
    @Test void partialReceiptUpdatesQuantitiesAndLinksRealBatch() {
        var result=service.receive(actor,20L,receipt("r1",2));
        assertEquals(2,line.getReceivedQuantity());assertEquals("PARTIALLY_RECEIVED",order.getStatus());
        assertEquals(new BigDecimal("5.11"),result.total());assertEquals(50L,result.items().getFirst().stockBatchId());
        verify(journal).post(eq(1L),eq("GOODS_RECEIPT"),eq(40L),anyString(),eq("1200"),eq("2110"),eq(new BigDecimal("5.11")));
    }
    @Test void allPartialReceiptValuesSumExactlyToOrderValue() {
        var first=service.receive(actor,20L,receipt("r1",2));
        var second=service.receive(actor,20L,receipt("r2",3));
        assertEquals(order.getTotal(),first.total().add(second.total()));assertEquals("RECEIVED",order.getStatus());
    }
    @Test void overReceiptRejectedBeforeChangingStock() {
        assertThrows(IllegalArgumentException.class,()->service.receive(actor,20L,receipt("r1",6)));
        assertEquals(0,line.getReceivedQuantity());verifyNoInteractions(ledger,journal);verify(receipts,never()).saveAndFlush(any());
    }
    @Test void repeatedReceiptKeyReturnsOriginalWithoutReceivingTwice() {
        service.receive(actor,20L,receipt("r1",2));
        var capture=org.mockito.ArgumentCaptor.forClass(GoodsReceipt.class);verify(receipts).saveAndFlush(capture.capture());
        when(receipts.findByStoreIdAndRequestKey(1L,"r1")).thenReturn(Optional.of(capture.getValue()));
        clearInvocations(ledger,journal,receipts);
        var retry=service.receive(actor,20L,receipt("r1",2));assertEquals(40L,retry.id());assertEquals(2,line.getReceivedQuantity());
        verifyNoInteractions(ledger,journal);verify(receipts,never()).saveAndFlush(any());
    }
    @Test void changedPayloadWithSameReceiptKeyIsRejected() {
        service.receive(actor,20L,receipt("r1",2));
        var capture=org.mockito.ArgumentCaptor.forClass(GoodsReceipt.class);verify(receipts).saveAndFlush(capture.capture());
        when(receipts.findByStoreIdAndRequestKey(1L,"r1")).thenReturn(Optional.of(capture.getValue()));
        assertThrows(IllegalArgumentException.class,()->service.receive(actor,20L,receipt("r1",1)));
        assertEquals(2,line.getReceivedQuantity());
    }
    @Test void otherStoreOrderCannotBeReceived() {
        assertThrows(IllegalArgumentException.class,()->service.receive(new AccessService.Actor(8L,2L,"ADMIN"),20L,receipt("r1",1)));
        verifyNoInteractions(ledger,journal);
    }
    @Test void duplicateOrderItemsCannotBypassRemainingQuantity() {
        var input=new PurchasingService.ReceiptInput("r1",List.of(new PurchasingService.ReceiptLine(30L,3,null),new PurchasingService.ReceiptLine(30L,3,null)));
        assertThrows(IllegalArgumentException.class,()->service.receive(actor,20L,input));verifyNoInteractions(ledger,journal);
    }
    @Test void cannotCancelAnOrderWithAnyReceipt() {
        line.setReceivedQuantity(1);assertThrows(IllegalArgumentException.class,()->service.cancel(actor,20L));
        assertEquals("ORDERED",order.getStatus());
    }
    @Test void supplierFromAnotherStoreCannotBeUsed() {
        assertThrows(IllegalArgumentException.class,()->service.createOrder(new AccessService.Actor(8L,2L,"ADMIN"),
            new PurchasingService.OrderInput("po-key",11L,"",List.of(new PurchasingService.OrderLine("P001",1,BigDecimal.ONE)))));
        verifyNoInteractions(ledger,journal);
    }
}
