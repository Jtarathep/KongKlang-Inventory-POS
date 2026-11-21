package com.kongklang.controller;
import com.kongklang.service.*;
import com.kongklang.entity.Sale;
import com.kongklang.config.BusinessCsrfInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SaleApiTest {
    SaleService sales;AccessService access;MockHttpSession session;MockMvc mvc;
    AccessService.Actor actor=new AccessService.Actor(7L,2L,"SALES");
    @BeforeEach void setup() {
        sales=mock(SaleService.class);access=mock(AccessService.class);session=new MockHttpSession();session.setAttribute("csrfToken","token");
        when(access.require(eq(session),any(String[].class))).thenReturn(actor);
        mvc=MockMvcBuilders.standaloneSetup(new SaleApiController(sales,mock(StoreContextService.class),access)).addInterceptors(new BusinessCsrfInterceptor()).build();
    }
    String payload(){return "{\"requestKey\":\"stable-key\",\"productCodes\":[\"P001\"],\"quantities\":[3]}";}
    @Test void missingCsrfDoesNotReachCheckout() throws Exception {
        var result=mvc.perform(post("/api/sales").session(session).contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn();
        assertEquals(403,result.getResponse().getStatus());verifyNoInteractions(sales);
    }
    @Test void checkoutUsesTrustedActorAndReturnsExactDecimalTotal() throws Exception {
        Sale sale=new Sale();ReflectionTestUtils.setField(sale,"id",10L);sale.setSaleNumber("S-TEST");sale.setTotalAmountValue(new BigDecimal("0.30"));sale.setPaymentMethod("CASH");sale.setPaymentStatus("PAID");
        when(sales.checkout("stable-key",List.of("P001"),List.of(3),actor)).thenReturn(sale);
        var result=mvc.perform(post("/api/sales").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn();
        assertEquals(200,result.getResponse().getStatus());assertTrue(result.getResponse().getContentAsString().contains("0.30"));
        assertFalse(result.getResponse().getContentAsString().contains("totalCost"));verify(sales).checkout("stable-key",List.of("P001"),List.of(3),actor);
    }
    @Test void concurrentDuplicateSignalsRetryWithoutExposingSql() throws Exception {
        when(sales.checkout(anyString(),any(),any(),any())).thenThrow(new DataIntegrityViolationException("secret SQL"));
        var result=mvc.perform(post("/api/sales").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn();
        assertEquals(409,result.getResponse().getStatus());assertFalse(result.getResponse().getContentAsString().contains("secret SQL"));
    }
    @Test void invalidCheckoutReturnsStructuredError() throws Exception {
        when(sales.checkout(anyString(),any(),any(),any())).thenThrow(new IllegalArgumentException("Invalid quantity"));
        var result=mvc.perform(post("/api/sales").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn();
        assertEquals(400,result.getResponse().getStatus());assertTrue(result.getResponse().getContentAsString().contains("Invalid quantity"));
    }
    @Test void uncertainTransactionCompletionIsNotReportedAsValidationFailure() throws Exception {
        when(sales.checkout(anyString(),any(),any(),any())).thenThrow(new org.springframework.transaction.TransactionSystemException("unknown commit outcome"));
        var result=mvc.perform(post("/api/sales").session(session).header("X-CSRF-Token","token").contentType(MediaType.APPLICATION_JSON).content(payload())).andReturn();
        assertEquals(500,result.getResponse().getStatus());assertFalse(result.getResponse().getContentAsString().contains("unknown commit outcome"));
    }
    @Test void allocationLookupDoesNotReturnAnotherStoresSale() throws Exception {
        when(access.require(eq(session),any(String[].class))).thenReturn(new AccessService.Actor(7L,2L,"ACCOUNTING"));
        var result=mvc.perform(get("/api/sales/42/allocations").session(session)).andReturn();
        assertEquals(404,result.getResponse().getStatus());verify(sales).getSaleById(42L,2L);verify(sales,never()).getAllocations(anyLong(),anyLong());
    }
}
