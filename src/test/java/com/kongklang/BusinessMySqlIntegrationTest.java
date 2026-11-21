package com.kongklang;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import com.kongklang.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only. Refuses to write to any catalog except the two named disposable test catalogs. */
@SpringBootTest(properties="app.bootstrap.enabled=false")
@ActiveProfiles("testshop")
@EnabledIfEnvironmentVariable(named="KONGKLANG_RUN_MYSQL_TESTS",matches="true")
class BusinessMySqlIntegrationTest {
    @Autowired DataSource datasource;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired StoreRepository stores;
    @Autowired UserAccountRepository users;
    @Autowired ProductRepository products;
    @Autowired StockBatchRepository batches;
    @Autowired PurchasingService purchasing;
    @Autowired PayableService payables;
    @Autowired JournalService journals;
    @Autowired StockLedgerService ledger;
    @Autowired SaleService saleService;
    @Autowired SaleRepository sales;
    @Autowired StoreTaxService taxSettings;
    @Autowired SaleReturnService saleReturns;
    @Autowired AccountingPeriodService accountingPeriods;
    @Autowired AdvancedAccountingService advancedAccounting;
    @Autowired PasswordService passwords;
    @Autowired AccessService access;
    @Autowired StoreAnalysisService storeAnalysis;
    @Autowired InventoryOperationService inventory;
    @Autowired InventoryOperationRepository inventoryOperations;
    @Autowired ManualPayableService manualPayables;
    @Autowired OverviewService overview;
    @Autowired CategoryService categories;
    @Autowired LegacyInventoryReview legacyReview;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    AccessService.Actor actor;Long storeId;Long supplierId;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));

    @BeforeEach void seed() throws Exception {
        try(var connection=datasource.getConnection()) {
            assertTrue(Set.of("kongklang_test","kongklang_integration").contains(connection.getCatalog()),
                    "Integration tests require a named disposable test database");
        }
        actor=transactions.execute(status->{
            Store store=new Store();store.setStoreCode("IT-"+UUID.randomUUID());store.setStoreName("Integration test");store.setActive(true);stores.saveAndFlush(store);
            UserAccount user=new UserAccount();user.setUsername("it-"+UUID.randomUUID());user.setDisplayName("Integration");
            user.setPassword("unused-test-only");user.setRole("ADMIN");user.setActive(true);user.setStore(store);users.saveAndFlush(user);
            for(String code:List.of("P001","P002")) {
                Product p=new Product();p.setStore(store);p.setCode(code);p.setName(code);p.setQuantity(0);p.setCostPrice(3.5);p.setPrice(5.0);products.save(p);
            }
            return new AccessService.Actor(user.getId(),store.getId(),"ADMIN");
        });
        storeId=actor.storeId();supplierId=purchasing.saveSupplier(actor,null,
                new PurchasingService.SupplierInput("SUP","Integration supplier","","","","",true)).id();
    }

    @AfterEach void cleanupOnlyThisFixtureStore() {
        if(storeId==null) return;
        transactions.executeWithoutResult(status->{
            jdbc.update("delete from audit_logs where store_id=?",storeId);
            jdbc.update("delete from accounting_period_balances where event_id in (select e.id from accounting_period_events e join accounting_periods p on p.id=e.period_id where p.store_id=?)",storeId);
            jdbc.update("delete from accounting_period_events where period_id in (select id from accounting_periods where store_id=?)",storeId);
            jdbc.update("delete from accounting_periods where store_id=?",storeId);
            jdbc.update("delete from legacy_inventory_reviews where store_id=?",storeId);
            jdbc.update("delete from categories where store_id=?",storeId);
            jdbc.update("delete from gl_journal_lines where journal_id in (select id from gl_journals where store_id=?)",storeId);
            jdbc.update("delete from gl_journals where store_id=?",storeId);
            jdbc.update("delete from gl_accounts where store_id=?",storeId);
            jdbc.update("delete from sale_return_allocations where return_item_id in (select i.id from sale_return_items i join sale_returns r on r.id=i.return_id where r.store_id=?)",storeId);
            jdbc.update("delete from sale_refund_tenders where return_id in (select id from sale_returns where store_id=?)",storeId);
            jdbc.update("delete from sale_return_items where return_id in (select id from sale_returns where store_id=?)",storeId);
            jdbc.update("delete from sale_returns where store_id=?",storeId);
            jdbc.update("delete from sale_allocations where store_id=?",storeId);
            jdbc.update("delete from sale_tenders where sale_id in (select id from sales where store_id=?)",storeId);
            jdbc.update("delete from sale_items where sale_id in (select id from sales where store_id=?)",storeId);
            jdbc.update("delete from sales where store_id=?",storeId);
            jdbc.update("delete from ap_payments where store_id=?",storeId);
            jdbc.update("delete from ap_invoices where store_id=?",storeId);
            jdbc.update("delete from goods_receipt_items where receipt_id in (select id from goods_receipts where store_id=?)",storeId);
            jdbc.update("delete from goods_receipts where store_id=?",storeId);
            jdbc.update("delete from purchase_order_items where purchase_order_id in (select id from purchase_orders where store_id=?)",storeId);
            jdbc.update("delete from purchase_orders where store_id=?",storeId);
            jdbc.update("delete from suppliers where store_id=?",storeId);
            jdbc.update("delete from stock_transactions where store_id=?",storeId);
            jdbc.update("delete from inventory_operation_items where operation_id in (select id from inventory_operations where store_id=?)",storeId);
            jdbc.update("delete from inventory_operations where store_id=?",storeId);
            jdbc.update("delete from stock_batches where store_id=?",storeId);
            jdbc.update("delete from products where store_id=?",storeId);
            jdbc.update("delete from app_users where store_id=?",storeId);
            jdbc.update("delete from stores where id=?",storeId);
        });
    }

    @Test void orderReceiptInvoiceAndPaymentsPersistAsOneConsistentFlow() {
        var order=purchasing.createOrder(actor,new PurchasingService.OrderInput(UUID.randomUUID().toString(),supplierId,"",
            List.of(new PurchasingService.OrderLine("P001",2,new BigDecimal("3.5000")))));
        var request=new PurchasingService.ReceiptInput(UUID.randomUUID().toString(),
            List.of(new PurchasingService.ReceiptLine(order.items().getFirst().id(),2,today.plusDays(30))));
        var receipt=purchasing.receive(actor,order.id(),request);
        assertEquals(receipt.id(),purchasing.receive(actor,order.id(),request).id());
        assertEquals(2,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        assertEquals(1,batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId,"P001").size());
        var invoice=payables.invoice(actor,new PayableService.InvoiceInput(receipt.id(),"INV-IT",today,today.plusDays(30)));
        payables.pay(actor,invoice.id(),new PayableService.PaymentInput(UUID.randomUUID().toString(),new BigDecimal("3.00"),today,""));
        var payment=new PayableService.PaymentInput(UUID.randomUUID().toString(),new BigDecimal("4.00"),today,"");
        assertEquals("PAID",payables.pay(actor,invoice.id(),payment).status());
        assertEquals("PAID",payables.pay(actor,invoice.id(),payment).status());
        assertEquals(4,journals.list(storeId).size());
        var balances=journals.trialBalance(storeId);
        assertEquals(0,balances.stream().map(JournalService.BalanceView::debit).reduce(BigDecimal.ZERO,BigDecimal::add)
            .compareTo(balances.stream().map(JournalService.BalanceView::credit).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }

    @Test void failedSecondReceiptLineRollsBackTheFirstLineAndDocument() {
        transactions.executeWithoutResult(status->{Product p=products.findByStoreIdAndCode(storeId,"P002").orElseThrow();p.setQuantity(5);products.save(p);});
        var order=purchasing.createOrder(actor,new PurchasingService.OrderInput(UUID.randomUUID().toString(),supplierId,"",List.of(
            new PurchasingService.OrderLine("P001",1,BigDecimal.ONE),new PurchasingService.OrderLine("P002",1,BigDecimal.ONE))));
        var items=order.items().stream().map(i->new PurchasingService.ReceiptLine(i.id(),1,null)).toList();
        assertThrows(IllegalStateException.class,()->purchasing.receive(actor,order.id(),new PurchasingService.ReceiptInput(UUID.randomUUID().toString(),items)));
        assertEquals(0,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        assertTrue(batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId,"P001").isEmpty());
        assertTrue(purchasing.receipts(storeId).isEmpty());assertTrue(journals.list(storeId).isEmpty());
        assertTrue(purchasing.order(storeId,order.id()).items().stream().allMatch(i->i.receivedQuantity()==0));
    }

    @Test void simultaneousWithdrawalsCannotOversellTheSameProduct() throws Exception {
        ledger.receive("P001",5,3.5,null,null,"Integration opening",storeId);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);AtomicInteger success=new AtomicInteger();
        try {
            List<Future<?>> results=new ArrayList<>();
            for(int i=0;i<2;i++) results.add(pool.submit(()->{
                try {start.await();ledger.consume("P001",4,"Integration sale",storeId,false);success.incrementAndGet();}
                catch(IllegalArgumentException expected) { /* Only one request has enough stock. */ }
                catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new RuntimeException(interrupted);}
            }));
            start.countDown();for(Future<?> result:results)result.get(15,TimeUnit.SECONDS);
            assertEquals(1,success.get());assertEquals(1,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
            assertEquals(1,batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId,"P001").stream().mapToInt(StockBatch::getRemainingQuantity).sum());
        } finally {pool.shutdownNow();}
    }

    @Test void cashSalePersistsAllocationAndTwoJournalsExactlyOnce() {
        ledger.receive("P001",3,3.5,null,null,"Integration opening",storeId);
        String key=UUID.randomUUID().toString();Sale first=saleService.checkout(key,List.of("P001"),List.of(2),actor);
        Sale retry=saleService.checkout(key,List.of("P001"),List.of(2),actor);
        assertEquals(first.getId(),retry.getId());assertEquals(new BigDecimal("10.00"),first.getTotalAmountValue());
        assertEquals(new BigDecimal("7.00"),first.getTotalCost());assertEquals(2,journals.list(storeId).size());
        assertEquals(1,saleService.getAllocations(first.getId(),storeId).size());
        assertEquals(1,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
    }

    @Test void failedCostPostingRollsBackSaleStockAllocationAndRevenueJournal() {
        ledger.receive("P001",10000,999999999.9999,null,null,"Integration opening",storeId);
        String key=UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class,()->saleService.checkout(key,List.of("P001"),List.of(10000),actor));
        assertTrue(sales.findByStoreIdAndRequestKey(storeId,key).isEmpty());assertTrue(journals.list(storeId).isEmpty());
        assertEquals(10000,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        assertEquals(10000,batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId,"P001").getFirst().getRemainingQuantity());
    }

    @Test void manualReceiptCountAndWriteOffReconcileStockToPostedInventoryExactlyOnce() {
        var receipt=new InventoryOperationService.Input("receipt-"+UUID.randomUUID(),"RECEIPT","P001",4,null,null,new BigDecimal("2.5000"),null,null,"Supplier delivery pending invoice");
        var first=inventory.execute(actor,receipt);assertEquals(first.id(),inventory.execute(actor,receipt).id());
        Product before=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();
        var count=new InventoryOperationService.Input("count-"+UUID.randomUUID(),"ADJUST","P001",3,4,before.getVersion(),null,null,null,"Physical count shortage");
        inventory.execute(actor,count);
        inventory.execute(actor,new InventoryOperationService.Input("out-"+UUID.randomUUID(),"OUT","P001",1,null,null,null,null,null,"Damaged item"));
        assertEquals(2,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        BigDecimal lotValue=batches.findAllByStoreId(storeId).stream().map(StockBatch::getRemainingValue).reduce(BigDecimal.ZERO,BigDecimal::add);
        var stock=journals.trialBalance(storeId).stream().filter(b->b.accountCode().equals("1200")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5.00"),lotValue);assertEquals(lotValue,stock.debit().subtract(stock.credit()));
        assertEquals(3,inventory.list(actor).size());assertEquals(3,journals.list(storeId).size());
    }

    @Test void unpostableManualReceiptRollsBackDocumentLotHistoryAndProductVersion() {
        Product before=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();String key="large-"+UUID.randomUUID();
        var request=new InventoryOperationService.Input(key,"RECEIPT","P001",10000,null,null,new BigDecimal("999999999.9999"),null,null,"Out of supported journal range");
        assertThrows(IllegalArgumentException.class,()->inventory.execute(actor,request));
        assertTrue(inventoryOperations.findByStoreIdAndRequestKey(storeId,key).isEmpty());assertTrue(batches.findAllByStoreId(storeId).isEmpty());
        Product after=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();assertEquals(0,after.getQuantity());assertEquals(before.getVersion(),after.getVersion());
        assertTrue(journals.list(storeId).isEmpty());assertEquals(0,jdbc.queryForObject("select count(*) from stock_transactions where store_id=?",Integer.class,storeId));
    }

    @Test void physicalCountRejectsVersionAfterQuantityChangesAwayAndBack() {
        inventory.execute(actor,new InventoryOperationService.Input("first-"+UUID.randomUUID(),"RECEIPT","P001",2,null,null,BigDecimal.ONE,null,null,"First receipt"));
        Product counted=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();
        inventory.execute(actor,new InventoryOperationService.Input("out-"+UUID.randomUUID(),"OUT","P001",1,null,null,null,null,null,"Write off"));
        inventory.execute(actor,new InventoryOperationService.Input("back-"+UUID.randomUUID(),"RECEIPT","P001",1,null,null,BigDecimal.ONE,null,null,"New receipt"));
        assertEquals(counted.getQuantity(),products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        var stale=new InventoryOperationService.Input("stale-"+UUID.randomUUID(),"ADJUST","P001",1,2,counted.getVersion(),null,null,null,"Old count");
        assertThrows(IllegalArgumentException.class,()->inventory.execute(actor,stale));assertEquals(3,inventory.list(actor).size());
    }

    org.springframework.mock.web.MockHttpSession session() {
        var s=new org.springframework.mock.web.MockHttpSession();s.setAttribute("userId",actor.userId());s.setAttribute("storeId",storeId);
        s.setAttribute("role","ADMIN");s.setAttribute("csrfToken","integration-token");return s;
    }
    org.springframework.test.web.servlet.MockMvc mvc(){return org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).build();}

    @Test void manualReceiptInvoicePaymentAndReportDoNotReceiveStockTwice() {
        var receipt=inventory.execute(actor,new InventoryOperationService.Input("manual-"+UUID.randomUUID(),"RECEIPT","P001",3,null,null,new BigDecimal("2.5000"),null,today.plusDays(10),"Manual supplier delivery"));
        var input=new ManualPayableService.Input(receipt.id(),supplierId,"MANUAL-IT",today,today.plusDays(30));
        var invoice=manualPayables.invoice(actor,input);assertEquals(invoice.id(),manualPayables.invoice(actor,input).id());
        assertEquals(new BigDecimal("7.50"),invoice.amount());
        assertEquals("PAID",payables.pay(actor,invoice.id(),new PayableService.PaymentInput(UUID.randomUUID().toString(),invoice.amount(),today,"")).status());
        assertEquals(3,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        saleService.checkout("sold-"+UUID.randomUUID(),List.of("P001"),List.of(1),actor);
        var report=overview.report(storeId,today,today);assertEquals(new BigDecimal("5.00"),report.revenue());
        assertEquals(new BigDecimal("2.50"),report.cost());assertEquals(new BigDecimal("2.50"),report.grossProfit());
        assertEquals(new BigDecimal("5.00"),report.getInventoryValue());assertEquals(0,report.getMismatchCount());
        assertEquals(2,report.stock().stream().filter(s->s.code().equals("P001")).findFirst().orElseThrow().expiring());
        assertEquals(4,jdbc.queryForObject("select count(*) from audit_logs where store_id=?",Integer.class,storeId));
    }

    @Test void countedIncreaseKeepsExplicitLotCostAndExpiry() {
        Product product=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();
        inventory.execute(actor,new InventoryOperationService.Input("counted-"+UUID.randomUUID(),"ADJUST","P001",2,0,product.getVersion(),new BigDecimal("1.2500"),null,today.plusDays(7),"Found two additional items"));
        var lot=batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId,"P001").getFirst();
        assertEquals(today.plusDays(7),lot.getExpiryDate());assertEquals(new BigDecimal("1.2500"),lot.getPurchaseCostValue());assertEquals(new BigDecimal("2.50"),lot.getRemainingValue());
    }

    @Test void csrfCoversCategoriesManagementAndLogoutAndGetCannotDelete() throws Exception {
        var mvc=mvc();var session=session();
        for(String path:List.of("/api/categories","/management/users/create","/logout"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/products/delete/1").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/categories").session(session)
            .header("X-CSRF-Token","integration-token").contentType("application/json").content("{\"name\":\"HTTP category\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        jdbc.update("delete from categories where store_id=?",storeId);
    }

    @Test void tenantAdminCannotListOrModifyAnotherStoresUsersOrPromoteSelf() throws Exception {
        var mvc=mvc();var session=session();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/management/stores").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/management/users/create").session(session)
            .param("_csrf","integration-token").param("username","forged").param("password","SomePass123!").param("displayName","forged")
            .param("role","OWNER").param("storeId",storeId.toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/management/users/create").session(session)
            .param("_csrf","integration-token").param("username","forged").param("password","SomePass123!").param("displayName","forged")
            .param("role","SALES").param("storeId","-1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test void actualThymeleafPagesRenderAgainstMySqlWithoutTemplateErrors() throws Exception {
        var receipt=inventory.execute(actor,new InventoryOperationService.Input("render-"+UUID.randomUUID(),"RECEIPT","P001",2,null,null,BigDecimal.ONE,null,null,"Page render fixture"));
        for(String path:List.of("/","/products","/products/new","/categories","/sales","/sales/history","/stock","/stock/in","/stock/out","/stock/adjust","/stock/history","/stock/batches","/purchasing","/accounting","/management/users","/reports","/reports/overview","/reports/audit","/stock/legacy-review","/stock/operations","/stock/operations/"+receipt.id())) {
            mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).session(session()))
                .andExpect(result->assertEquals(200,result.getResponse().getStatus(),path));
        }
    }

    @Test void disabledProductCannotBeSoldAndReadApiNeverSerializesPasswords() throws Exception {
        ledger.receive("P001",2,1.0,null,null,"Receipt",storeId);
        transactions.executeWithoutResult(status->{Product p=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();p.setActive(false);products.save(p);});
        assertThrows(IllegalArgumentException.class,()->saleService.checkout(UUID.randomUUID().toString(),List.of("P001"),List.of(1),actor));
        assertEquals(2,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        String json=mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products").session(session()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("password"));
    }

    @Test void legacyOpeningRequiresExplicitConfirmationPreservesOldDataAndCannotBeRepeatedAsNew() {
        ledger.receive("P001",2,3.5,null,null,"Synthetic legacy owned lot",storeId);
        transactions.executeWithoutResult(status->{Product p=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();p.setQuantity(20);products.save(p);});
        Product old=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();
        var input=new LegacyInventoryReview.Input("verified-"+UUID.randomUUID(),"P001",20,old.getVersion(),true,"Verified synthetic physical count and supplier cost",List.of(new LegacyInventoryReview.Lot(4,new BigDecimal("2.0000"),today.plusDays(30))));
        var doc=legacyReview.confirm(actor,input);assertEquals(doc.id(),legacyReview.confirm(actor,input).id());
        assertTrue(legacyReview.snapshot(storeId,doc.id()).contains("\"productQuantity\":20"));
        assertTrue(legacyReview.snapshot(storeId,doc.id()).contains("\"remaining\":2"));
        assertEquals(4,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        assertEquals(new BigDecimal("8.00"),overview.report(storeId,today,today).getInventoryValue());
        Product now=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();
        var repeated=new LegacyInventoryReview.Input("another-"+UUID.randomUUID(),"P001",4,now.getVersion(),true,"Attempted new opening",input.lots());
        assertThrows(IllegalArgumentException.class,()->legacyReview.confirm(actor,repeated));
        assertThrows(IllegalArgumentException.class,()->legacyReview.confirm(actor,new LegacyInventoryReview.Input("no-confirm","P002",0,0L,false,"unconfirmed",List.of())));
        saleService.checkout("after-opening",List.of("P001"),List.of(1),actor);
        assertEquals(3,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
    }

    @Test void failedVerifiedOpeningRollsBackOriginalLotsSnapshotDocumentAndAudit() {
        ledger.receive("P001",2,3.5,null,null,"Synthetic legacy lot",storeId);
        Product old=products.findByStoreIdAndCode(storeId,"P001").orElseThrow();
        String key="bad-baseline-"+UUID.randomUUID();
        var input=new LegacyInventoryReview.Input(key,"P001",2,old.getVersion(),true,"Synthetic amount outside supported posting range",List.of(new LegacyInventoryReview.Lot(10000,new BigDecimal("999999999.9999"),null)));
        assertThrows(IllegalArgumentException.class,()->legacyReview.confirm(actor,input));
        assertEquals(2,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        var original=batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId,"P001");assertEquals(1,original.size());assertEquals(2,original.getFirst().getRemainingQuantity());
        assertEquals(new BigDecimal("7.00"),original.getFirst().getRemainingValue());assertTrue(inventoryOperations.findByStoreIdAndRequestKey(storeId,key).isEmpty());
        assertEquals(0,jdbc.queryForObject("select count(*) from legacy_inventory_reviews where store_id=?",Integer.class,storeId));
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_logs where store_id=?",Integer.class,storeId));
    }

    @Test void staleSessionCannotForgeStoreOrElevateRole() throws Exception {
        var session=session();session.setAttribute("storeId",-999L);session.setAttribute("role","SYSTEM_ADMIN");
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(storeId,session.getAttribute("storeId"));assertEquals("ADMIN",session.getAttribute("role"));
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/management/stores").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test void productWebFormPersistsBarcodeDescriptionAndInactiveStatusWithZeroOpening() throws Exception {
        var session=session();Category category=new Category();category.setName("Web fixture");categories.saveCategory(category,session);
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/products/save").session(session)
            .param("_csrf","integration-token").param("code","WEB-NEW").param("name","Web created product").param("barcode","8850012345678")
            .param("category","Web fixture").param("unit","ขวด").param("quantity","0").param("price","15.25").param("costPrice","7.1250")
            .param("description","Product details").param("active","false"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/products"));
        Product saved=products.findByStoreIdAndCode(storeId,"WEB-NEW").orElseThrow();
        assertEquals("8850012345678",saved.getBarcode());assertEquals("Product details",saved.getDescription());assertFalse(saved.getActive());assertEquals(0,saved.getQuantity());
        assertEquals(new BigDecimal("15.25"),saved.getPriceValue());assertEquals(new BigDecimal("7.1250"),saved.getCostPriceValue());
        Category renamed=categories.getAllCategories(session).getFirst();renamed.setName("Renamed web fixture");categories.saveCategory(renamed,session);
        assertEquals("Renamed web fixture",products.findByStoreIdAndCode(storeId,"WEB-NEW").orElseThrow().getCategory());
        assertThrows(IllegalArgumentException.class,()->categories.deleteCategory(renamed.getId(),session));
    }


    private Sale commerceSale(String key,boolean vat,int quantity) {
        if(vat)taxSettings.save(actor,new StoreTaxService.Input("EXCLUSIVE",new BigDecimal("7"),true,"Test issuer","Test address","0105551001234","00000",0));
        transactions.executeWithoutResult(tx->ledger.receiveBatchValue("P001",quantity,new BigDecimal("0.0050"),new BigDecimal("100.00"),today.plusDays(30),"commerce fixture",storeId));
        var discount=new SalePricing.Discount(SalePricing.DiscountType.PERCENT,new BigDecimal("10"));
        var q=saleService.quote(List.of("P001"),List.of(quantity),discount,actor);
        var payment=new CheckoutTerms.Payment(CheckoutTerms.Method.TRANSFER,q.pricing().payable(),null,"TEST-TRANSFER");
        return saleService.checkout(key,List.of("P001"),List.of(quantity),actor,new CheckoutTerms(discount,List.of(payment),q.pricing().payable(),true,q.taxRevision(),CheckoutTerms.DocumentType.RECEIPT,null));
    }
    @Test void commerceTaxSnapshotAndBankJournalAreExactAndDoNotChangeWithSettings() {
        Sale sale=commerceSale("commerce-tax",true,3);
        assertEquals(new BigDecimal("270.00"),sale.getNetAmount());assertEquals(new BigDecimal("18.90"),sale.getTaxAmount());assertEquals(new BigDecimal("288.90"),sale.getTotalAmountValue());
        assertEquals(0,new BigDecimal("270.00").compareTo(saleService.getDailyReport(today,storeId).getRevenueValue()));
        assertEquals(0,new BigDecimal("18.90").compareTo(journals.trialBalance(storeId).stream().filter(b->b.accountCode().equals("2200")).findFirst().orElseThrow().credit()));
        taxSettings.save(actor,new StoreTaxService.Input("NONE",new BigDecimal("5"),false,"","","","",1));
        Sale unchanged=saleService.getSaleById(sale.getId(),storeId);assertEquals(new BigDecimal("18.90"),unchanged.getTaxAmount());assertEquals("EXCLUSIVE",unchanged.getTaxMode());
        assertEquals(1,jdbc.queryForObject("select count(*) from sale_tenders where sale_id=?",Integer.class,sale.getId()));
    }
    @Test void commerceStalePriceRollsBackSaleStockTenderAndAllJournals() {
        transactions.executeWithoutResult(tx->ledger.receiveBatchValue("P001",2,new BigDecimal("2"),new BigDecimal("100"),null,"fixture",storeId));
        var terms=new CheckoutTerms(null,List.of(new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,new BigDecimal("99"),null,"")),new BigDecimal("99"),true,0L,CheckoutTerms.DocumentType.RECEIPT,null);
        assertThrows(IllegalArgumentException.class,()->saleService.checkout("stale-price",List.of("P001"),List.of(1),actor,terms));
        assertEquals(2,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());assertTrue(sales.findByStoreIdAndRequestKey(storeId,"stale-price").isEmpty());
        assertEquals(0,jdbc.queryForObject("select count(*) from gl_journals where store_id=? and source_type like 'SALE_%'",Integer.class,storeId));
    }
    @Test void partialReturnsConserveOriginalVatMoneyAndLotBookValueAndAreIdempotent() {
        Sale sale=commerceSale("partial-returns",true,3);Long item=saleService.getSaleItems(sale.getId(),storeId).getFirst().getId();
        BigDecimal refunded=BigDecimal.ZERO;
        for(int i=0;i<3;i++) {
            var lines=List.of(new SaleReturnService.LineInput(item,1,true));var q=saleReturns.quote(actor,sale.getId(),lines);
            var input=new SaleReturnService.Input("return-"+i,lines,List.of(new CheckoutTerms.Payment(CheckoutTerms.Method.TRANSFER,q.total(),null,"REFUND")),q.total(),q.revision(),true,"สินค้าคืนสภาพสมบูรณ์");
            var result=saleReturns.refund(actor,sale.getId(),input);assertEquals(result.id(),saleReturns.refund(actor,sale.getId(),input).id());refunded=refunded.add(result.total());
        }
        assertEquals(sale.getTotalAmountValue(),refunded);assertEquals(3,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        var snapshot=transactions.execute(tx->ledger.snapshot("P001",storeId));assertEquals(sale.getTotalCost(),snapshot.value());
        assertEquals(0,saleReturns.available(sale.getId(),storeId).getFirst().remaining());
        assertThrows(IllegalArgumentException.class,()->saleReturns.quote(actor,sale.getId(),List.of(new SaleReturnService.LineInput(item,1,true))));
        assertEquals(new BigDecimal("0.00"),journals.trialBalance(storeId).stream().filter(b->b.accountCode().equals("2200")).findFirst().orElseThrow().credit());
        assertEquals(0,saleService.getDailyReport(today,storeId).getRevenueValue().signum());
        assertEquals(0,overview.report(storeId,today,today).revenue().signum());
    }
    @Test void damagedReturnDoesNotInventStockAndCrossStoreLookupIsRejected() {
        Sale sale=commerceSale("damaged-return",false,1);Long item=saleService.getSaleItems(sale.getId(),storeId).getFirst().getId();
        var lines=List.of(new SaleReturnService.LineInput(item,1,false));var q=saleReturns.quote(actor,sale.getId(),lines);
        var input=new SaleReturnService.Input("damaged",lines,List.of(new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,q.total(),null,"")),q.total(),q.revision(),true,"บรรจุภัณฑ์เสียหาย");
        var result=saleReturns.refund(actor,sale.getId(),input);assertEquals(0,result.restockedCost().signum());assertEquals(0,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());
        assertThrows(IllegalArgumentException.class,()->saleReturns.available(sale.getId(),storeId+999999));
        assertThrows(IllegalArgumentException.class,()->saleReturns.refund(new AccessService.Actor(actor.userId(),storeId,"SALES"),sale.getId(),input));
    }
    @Test void concurrentRefundRetryCreatesOneDocumentAndOneRestockedUnit() throws Exception {
        Sale sale=commerceSale("concurrent-return",false,1);Long item=saleService.getSaleItems(sale.getId(),storeId).getFirst().getId();
        var lines=List.of(new SaleReturnService.LineInput(item,1,true));var q=saleReturns.quote(actor,sale.getId(),lines);
        var input=new SaleReturnService.Input("same-refund",lines,List.of(new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,q.total(),null,"")),q.total(),q.revision(),true,"คืนสินค้า");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->saleReturns.refund(actor,sale.getId(),input));var b=executor.submit(()->saleReturns.refund(actor,sale.getId(),input));assertEquals(a.get(15,TimeUnit.SECONDS).id(),b.get(15,TimeUnit.SECONDS).id());
        }
        assertEquals(1,products.findByStoreIdAndCode(storeId,"P001").orElseThrow().getQuantity());assertEquals(1,saleReturns.list(sale.getId(),storeId).size());
    }

    @Test void periodCloseSnapshotsBalancesTransfersProfitAndOwnerReopensByReversal() {
        commerceSale("period-sale",true,1);YearMonth month=YearMonth.from(today);
        var before=advancedAccounting.income(storeId,month.atDay(1),today);var close=new AccountingPeriodService.Input(month.toString(),0,"ตรวจงบทดลองแล้ว",true);
        var period=accountingPeriods.close(actor,close);assertEquals("CLOSED",period.status());assertEquals(period.id(),accountingPeriods.close(actor,close).id());
        var after=advancedAccounting.balanceSheet(storeId,month.atEndOfMonth());assertEquals(0,after.unclosedProfit().signum());assertEquals(0,after.difference().signum());
        assertEquals(0,before.profit().compareTo(advancedAccounting.income(storeId,month.atDay(1),today).profit()));
        assertTrue(jdbc.queryForObject("select count(*) from accounting_period_balances b join accounting_period_events e on e.id=b.event_id where e.period_id=?",Integer.class,period.id())>0);
        assertThrows(IllegalArgumentException.class,()->transactions.executeWithoutResult(tx->ledger.adjust("P001",0,"ตรวจนับยอดเดิม",storeId)));
        assertThrows(IllegalArgumentException.class,()->transactions.executeWithoutResult(tx->journals.post(storeId,"ZERO_TEST",1L,"zero","1100","4000",BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class,()->saleService.checkout("closed-sale",List.of("P001"),List.of(1),actor));
        var reopen=new AccountingPeriodService.Input(month.toString(),period.revision(),"เจ้าของอนุมัติแก้ไข",true);
        assertThrows(IllegalArgumentException.class,()->accountingPeriods.reopen(actor,reopen));
        var owner=new AccessService.Actor(actor.userId(),storeId,"OWNER");var opened=accountingPeriods.reopen(owner,reopen);assertEquals("REOPENED",opened.status());
        assertEquals(0,before.profit().compareTo(advancedAccounting.balanceSheet(storeId,month.atEndOfMonth()).unclosedProfit()));
        assertEquals(2,jdbc.queryForObject("select count(*) from accounting_period_events where period_id=?",Integer.class,period.id()));
        transactions.executeWithoutResult(tx->ledger.adjust("P001",0,"ตรวจนับหลังเปิดงวด",storeId));
        var reclosed=accountingPeriods.close(actor,new AccountingPeriodService.Input(month.toString(),opened.revision(),"ปิดอีกครั้ง",true));assertEquals("CLOSED",reclosed.status());assertEquals(3,reclosed.revision());
    }
    @Test void advancedReportsScopeStoresAndSeparateVatFromOperatingProfit(){
        Sale sale=commerceSale("advanced-sale",true,1);var report=advancedAccounting.income(storeId,today,today);
        assertEquals(0,sale.getNetAmount().compareTo(report.revenue()));assertEquals(0,sale.getTotalCost().compareTo(report.expense()));assertEquals(0,advancedAccounting.balanceSheet(storeId,today).difference().signum());
        assertEquals(0,advancedAccounting.income(storeId+999999,today,today).revenue().signum());
        assertTrue(advancedAccounting.ledger(storeId+999999,"1110",today,today).lines().isEmpty());
        assertEquals(0,sale.getTotalAmountValue().compareTo(advancedAccounting.ledger(storeId,"1110",today,today).closing()));
    }
    @Test void temporaryPasswordInvalidatesOldSessionsAndMustBeChanged(){
        Long staff=transactions.execute(tx->{var u=new UserAccount();u.setStore(stores.findById(storeId).orElseThrow());u.setUsername("it-reset-"+UUID.randomUUID());u.setDisplayName("Test staff");u.setPassword("test-only");u.setRole("STAFF");return users.saveAndFlush(u).getId();});
        var old=new org.springframework.mock.web.MockHttpSession();old.setAttribute("userId",staff);old.setAttribute("credentialVersion",0L);
        assertEquals(staff,access.require(old).userId());
        assertThrows(IllegalArgumentException.class,()->passwords.reset(new AccessService.Actor(actor.userId(),storeId+99999,"ADMIN"),staff,"TemporaryTestOnly123!"));
        passwords.reset(actor,staff,"TemporaryTestOnly123!");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.require(old));
        var current=new org.springframework.mock.web.MockHttpSession();current.setAttribute("userId",staff);current.setAttribute("credentialVersion",1L);var staffActor=access.require(current);
        assertEquals(true,current.getAttribute("passwordChangeRequired"));
        passwords.change(staffActor,"TemporaryTestOnly123!","NewTestOnlyPassword456!");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.require(current));
        assertFalse(users.findById(staff).orElseThrow().isPasswordChangeRequired());
    }
    @Test void readOnlyAnalysisUsesScopedNetSalesAndDoesNotCreateDocuments(){
        commerceSale("analysis-sale",true,1);
        assertEquals("RULES",storeAnalysis.analyze(storeId,"sales").source());
        assertTrue(storeAnalysis.analyze(storeId,"sales").summary().contains("90.00"));
        assertTrue(storeAnalysis.analyze(storeId+99999,"best").items().isEmpty());
        assertEquals(1,jdbc.queryForObject("select count(*) from sales where store_id=?",Integer.class,storeId));
    }
}
