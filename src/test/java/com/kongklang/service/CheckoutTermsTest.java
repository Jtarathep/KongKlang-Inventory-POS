package com.kongklang.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutTermsTest {
    BigDecimal b(String s){return new BigDecimal(s);}
    CheckoutTerms.Payment cash(String applied,String received){return new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,b(applied),b(received),"");}
    @Test void mixedTenderAndCashChangeAreSeparateFromRevenue(){var t=new CheckoutTerms(null,List.of(cash("40","50"),new CheckoutTerms.Payment(CheckoutTerms.Method.TRANSFER,b("60"),null,"REF")),b("100"),true);assertEquals("MIXED",t.method());assertEquals(b("10.00"),t.payments().getFirst().change());}
    @Test void staffConfirmationIsRequired(){assertThrows(IllegalArgumentException.class,()->new CheckoutTerms(null,List.of(cash("100","100")),b("100"),false));}
    @Test void refusesShortOverAndDuplicateTenders(){assertThrows(IllegalArgumentException.class,()->new CheckoutTerms(null,List.of(cash("99","100")),b("100"),true));assertThrows(IllegalArgumentException.class,()->new CheckoutTerms(null,List.of(cash("101","101")),b("100"),true));assertThrows(IllegalArgumentException.class,()->new CheckoutTerms(null,List.of(cash("50","50"),cash("50","50")),b("100"),true));}
    @Test void onlyCashMayHaveChange(){assertThrows(IllegalArgumentException.class,()->new CheckoutTerms.Payment(CheckoutTerms.Method.CARD,b("100"),b("110"),""));assertThrows(IllegalArgumentException.class,()->cash("100","99"));}
    @Test void freeBillHasNoFictitiousTender(){var t=new CheckoutTerms(null,List.of(),b("0"),true);assertEquals("NONE",t.method());assertEquals(b("0.00"),t.expectedTotal());}
    @Test void fullTaxInvoiceNeedsBuyerDetails(){assertThrows(IllegalArgumentException.class,()->new CheckoutTerms(null,List.of(),b("0"),true,0L,CheckoutTerms.DocumentType.TAX_INVOICE,null));}
}
