package com.kongklang.controller;
import com.kongklang.service.*;
import com.kongklang.config.BusinessCsrfInterceptor;
import com.kongklang.repository.StockTransactionRepository;
import com.kongklang.entity.Product;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class InventoryApiTest {
    InventoryOperationService operations;ProductService products;AccessService access;MockHttpSession session;MockMvc mvc;
    AccessService.Actor actor=new AccessService.Actor(7L,2L,"WAREHOUSE");
    @BeforeEach void setup() {
        operations=mock(InventoryOperationService.class);products=mock(ProductService.class);access=mock(AccessService.class);
        session=new MockHttpSession();session.setAttribute("csrfToken","token");when(access.require(eq(session),any(String[].class))).thenReturn(actor);
        mvc=MockMvcBuilders.standaloneSetup(new StockApiController(products,mock(StockTransactionRepository.class),access,operations))
            .setControllerAdvice(new InventoryExceptionAdvice()).addInterceptors(new BusinessCsrfInterceptor()).build();
    }
    String payload(){return "{\"requestKey\":\"request-1\",\"kind\":\"ADJUST\",\"code\":\"P001\",\"quantity\":2,\"expectedQuantity\":3,\"expectedVersion\":4,\"note\":\"Count\"}";}
    InventoryOperationService.OperationView view(){return new InventoryOperationService.OperationView(10L,"IV-TEST","ADJUST","P001","Demo",3,2,new BigDecimal("7.50"),new BigDecimal("5.00"),7L,"Count",null,List.of());}
    @Test void csrfFailureDoesNotReachStockMutation()throws Exception {
        assertEquals(403,mvc.perform(post("/api/stock/operations").session(session).contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn().getResponse().getStatus());
        verifyNoInteractions(operations);
    }
    @Test void countPayloadIncludesVersionAndUsesTrustedActor()throws Exception {
        when(operations.execute(eq(actor),any())).thenReturn(view());
        var response=mvc.perform(post("/api/stock/operations").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("7.50"));
        var capture=org.mockito.ArgumentCaptor.forClass(InventoryOperationService.Input.class);verify(operations).execute(eq(actor),capture.capture());
        assertEquals(4L,capture.getValue().expectedVersion());assertEquals(3,capture.getValue().expectedQuantity());
    }
    @Test void uncertainCommitReturns500WithoutLeakingDiagnosticDetails()throws Exception {
        when(operations.execute(any(),any())).thenThrow(new org.springframework.transaction.TransactionSystemException("private database detail"));
        var response=mvc.perform(post("/api/stock/operations").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn().getResponse();
        assertEquals(500,response.getStatus());assertFalse(response.getContentAsString().contains("private database detail"));
    }
    @Test void duplicateConstraintPreservesRetryThrough409()throws Exception {
        when(operations.execute(any(),any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("SQL secret"));
        var response=mvc.perform(post("/api/stock/operations").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn().getResponse();
        assertEquals(409,response.getStatus());assertFalse(response.getContentAsString().contains("SQL secret"));
    }
    @Test void staleCountIsDefinitive400AndRoleDenialIs403()throws Exception {
        when(operations.execute(any(),any())).thenThrow(new IllegalArgumentException("Stale count"));
        assertEquals(400,mvc.perform(post("/api/stock/operations").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn().getResponse().getStatus());
        when(access.require(eq(session),any(String[].class))).thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(403,mvc.perform(post("/api/stock/operations").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn().getResponse().getStatus());
    }
    @Test void scannerUsesSameDurableKeyAndOneItemWithNoInventedPayment()throws Exception {
        Product p=new Product();p.setCode("P001");when(products.getProductByBarcode("8850001",session)).thenReturn(p);when(operations.execute(eq(actor),any())).thenReturn(view());
        var response=mvc.perform(post("/api/stock/scan-in").session(session).header("X-CSRF-Token","token").param("requestKey","scan-1").param("barcode","8850001")).andReturn().getResponse();
        assertEquals(200,response.getStatus());var capture=org.mockito.ArgumentCaptor.forClass(InventoryOperationService.Input.class);verify(operations).execute(eq(actor),capture.capture());
        assertEquals("scan-1",capture.getValue().requestKey());assertEquals(1,capture.getValue().quantity());assertNull(capture.getValue().unitCost());
    }
    @Test void legacyEndpointCannotSilentlyCreateNewKey()throws Exception {
        assertEquals(400,mvc.perform(post("/api/stock/scan-out").session(session).header("X-CSRF-Token","token").param("barcode","8850001").param("note","Write off")).andReturn().getResponse().getStatus());
        verifyNoInteractions(operations);
    }
}
