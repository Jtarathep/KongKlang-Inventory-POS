package com.kongklang.controller;
import com.kongklang.service.*;
import com.kongklang.config.BusinessCsrfInterceptor;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessApiTest {
    AccessService access;PurchasingService purchasing;MockHttpSession session;MockMvc mvc;
    @BeforeEach void setup() {
        access=mock(AccessService.class);purchasing=mock(PurchasingService.class);session=new MockHttpSession();session.setAttribute("csrfToken","token");
        mvc=MockMvcBuilders.standaloneSetup(new PurchasingApiController(access,purchasing))
            .setControllerAdvice(new BusinessExceptionAdvice()).addInterceptors(new BusinessCsrfInterceptor()).build();
    }
    @Test void missingCsrfBlocksMutationBeforeBusinessService() throws Exception {
        var result=mvc.perform(post("/api/purchasing/suppliers").session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn();assertEquals(403,result.getResponse().getStatus());verifyNoInteractions(access,purchasing);
    }
    @Test void invalidCsrfBlocksMutation() throws Exception {
        var result=mvc.perform(post("/api/purchasing/suppliers").session(session).header("X-CSRF-Token","wrong").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn();assertEquals(403,result.getResponse().getStatus());verifyNoInteractions(purchasing);
    }
    @Test void unauthorizedRoleCannotReceiveEvenWithValidCsrf() throws Exception {
        when(access.require(eq(session),any(String[].class))).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"denied"));
        var result=mvc.perform(post("/api/purchasing/orders/1/receipts").session(session).header("X-CSRF-Token","token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"x\",\"items\":[]}"))
            .andReturn();assertEquals(403,result.getResponse().getStatus());verifyNoInteractions(purchasing);
    }
    @Test void getUsesTrustedStoreAndDoesNotNeedMutationToken() throws Exception {
        when(access.require(eq(session),any(String[].class))).thenReturn(new AccessService.Actor(7L,2L,"ADMIN"));
        when(purchasing.orders(2L)).thenReturn(List.of());
        var result=mvc.perform(get("/api/purchasing/orders").session(session)).andReturn();
        assertEquals(200,result.getResponse().getStatus());assertEquals("[]",result.getResponse().getContentAsString());verify(purchasing).orders(2L);
    }
    @Test void serviceValidationReturnsActionableJsonError() throws Exception {
        when(access.require(eq(session),any(String[].class))).thenReturn(new AccessService.Actor(7L,1L,"ADMIN"));
        when(purchasing.createOrder(any(),any())).thenThrow(new IllegalArgumentException("Invalid quantity"));
        var result=mvc.perform(post("/api/purchasing/orders").session(session).header("X-CSRF-Token","token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":1,\"items\":[]}"))
            .andReturn();assertEquals(400,result.getResponse().getStatus());assertTrue(result.getResponse().getContentAsString().contains("Invalid quantity"));
    }
}
