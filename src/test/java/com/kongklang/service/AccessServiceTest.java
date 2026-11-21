package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessServiceTest {
    UserAccount user;UserAccountRepository users;AccessService service;MockHttpSession session;
    @BeforeEach void setup() {
        users=mock(UserAccountRepository.class);service=new AccessService(users);session=new MockHttpSession();session.setAttribute("userId",7L);
        Store store=new Store();store.setId(1L);store.setActive(true);user=new UserAccount();user.setId(7L);user.setStore(store);user.setActive(true);user.setRole("WAREHOUSE");
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }
    @Test void trustedStoreAndRoleComeFromDatabaseNotSession() {
        session.setAttribute("storeId",999L);session.setAttribute("role","OWNER");
        var actor=service.require(session,"WAREHOUSE");assertEquals(1L,actor.storeId());assertEquals("WAREHOUSE",actor.role());
        assertEquals(1L,session.getAttribute("storeId"));assertNotNull(session.getAttribute("csrfToken"));
    }
    @Test void forgedSessionRoleCannotGrantAccountingAccess() {
        session.setAttribute("role","ACCOUNTING");var error=assertThrows(ResponseStatusException.class,()->service.require(session,"ACCOUNTING"));assertEquals(403,error.getStatusCode().value());
    }
    @Test void disabledUserIsRejectedEvenWithExistingSession() {
        user.setActive(false);assertThrows(ResponseStatusException.class,()->service.require(session));
    }
    @Test void disabledStoreIsRejectedEvenWithExistingSession() {
        user.getStore().setActive(false);assertThrows(ResponseStatusException.class,()->service.require(session));
    }
    @Test void missingLoginCannotQueryStoreData() {
        assertThrows(ResponseStatusException.class,()->service.require(null));verifyNoInteractions(users);
    }
}
