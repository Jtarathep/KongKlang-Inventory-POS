package com.kongklang.service;

import com.kongklang.entity.Store;
import com.kongklang.repository.StoreRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreTaxServiceTest {
    StoreRepository stores=mock(StoreRepository.class);AuditTrail audit=mock(AuditTrail.class);
    StoreTaxService service=new StoreTaxService(stores,audit);Store store;
    AccessService.Actor actor=new AccessService.Actor(7L,2L,"ADMIN");
    @BeforeEach void setup(){store=new Store();store.setId(2L);when(stores.lockById(2L)).thenReturn(Optional.of(store));}
    StoreTaxService.Input input(String mode,String rate,boolean registered,long revision){return new StoreTaxService.Input(mode,new BigDecimal(rate),registered,"Test legal entity","Test address","0105551001234","00000",revision);}
    @Test void unregisteredDefaultDoesNotExtractSevenPercent(){assertEquals(SalePricing.TaxRule.none(),StoreTaxService.rule(store));}
    @Test void noVatCanKeepADormantRateWithoutChargingIt(){service.save(actor,input("NONE","7",false,0));assertEquals("NONE",store.getTaxMode());assertEquals(new BigDecimal("7.00"),store.getVatRate());assertEquals(1,store.getTaxRevision());assertEquals(SalePricing.TaxRule.none(),StoreTaxService.rule(store));verify(audit).record(eq(actor),eq("TAX_SETTINGS"),anyString());}
    @Test void registeredShopCanEnterItsOwnRate(){service.save(actor,input("INCLUSIVE","5.5",true,0));assertEquals(new BigDecimal("5.50"),StoreTaxService.rule(store).rate());}
    @Test void cannotEnableVatWhileUnregistered(){assertThrows(IllegalArgumentException.class,()->service.save(actor,input("EXCLUSIVE","7",false,0)));verify(stores,never()).save(any());}
    @Test void invoiceIssuerMustHaveCompleteRegistrationDetails(){var i=new StoreTaxService.Input("INCLUSIVE",new BigDecimal("7"),true,"","","","",0);assertThrows(IllegalArgumentException.class,()->service.save(actor,i));}
    @Test void staleSettingsAndOtherRolesCannotChangeStore(){assertThrows(IllegalArgumentException.class,()->service.save(actor,input("NONE","7",false,2)));assertThrows(IllegalArgumentException.class,()->service.save(new AccessService.Actor(8L,2L,"SALES"),input("NONE","7",false,0)));verify(stores,never()).save(any());}
    @Test void zeroRateIsDifferentFromNoVat(){service.save(actor,input("EXCLUSIVE","0",true,0));assertEquals(SalePricing.TaxMode.EXCLUSIVE,StoreTaxService.rule(store).mode());assertEquals(new BigDecimal("0.00"),StoreTaxService.rule(store).rate());}
}
