package com.kongklang.service;
import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalServiceTest {
    StoreRepository stores;GlAccountRepository accounts;GlJournalRepository journals;JournalService service;
    @BeforeEach void setup() {
        stores=mock(StoreRepository.class);accounts=mock(GlAccountRepository.class);journals=mock(GlJournalRepository.class);
        Store store=new Store();store.setId(1L);when(stores.lockById(1L)).thenReturn(Optional.of(store));
        when(accounts.save(any())).thenAnswer(i->i.getArgument(0));service=new JournalService(stores,accounts,journals);
    }
    @Test void postingCreatesBalancedDebitAndCreditInTheCorrectStore() {
        service.post(1L,"GR",10L,"GR1","1200","2110",new BigDecimal("25.50"));
        var capture=org.mockito.ArgumentCaptor.forClass(GlJournal.class);verify(journals).save(capture.capture());
        GlJournal j=capture.getValue();assertEquals(1L,j.getStore().getId());assertEquals(2,j.getItems().size());
        BigDecimal debit=j.getItems().stream().map(GlJournalLine::getDebit).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal credit=j.getItems().stream().map(GlJournalLine::getCredit).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(0,debit.compareTo(credit));assertEquals(0,debit.compareTo(new BigDecimal("25.50")));
    }
    @Test void sameSourceCannotPostTwice() {
        service.post(1L,"GR",10L,"GR1","1200","2110",new BigDecimal("25.50"));
        var capture=org.mockito.ArgumentCaptor.forClass(GlJournal.class);verify(journals).save(capture.capture());
        when(journals.findByStoreIdAndSourceTypeAndSourceId(1L,"GR",10L)).thenReturn(Optional.of(capture.getValue()));clearInvocations(journals);
        service.post(1L,"GR",10L,"GR1","1200","2110",new BigDecimal("25.50"));verify(journals,never()).save(any());
    }
    @Test void sameSourceWithChangedAmountIsRejected() {
        service.post(1L,"GR",10L,"GR1","1200","2110",new BigDecimal("25.50"));
        var capture=org.mockito.ArgumentCaptor.forClass(GlJournal.class);verify(journals).save(capture.capture());
        when(journals.findByStoreIdAndSourceTypeAndSourceId(1L,"GR",10L)).thenReturn(Optional.of(capture.getValue()));
        assertThrows(IllegalStateException.class,()->service.post(1L,"GR",10L,"GR1","1200","2110",new BigDecimal("25.51")));
    }
    @Test void zeroValueDoesNotCreateAnEmptyJournal() {
        service.post(1L,"GR",10L,"GR1","1200","2110",BigDecimal.ZERO);verifyNoInteractions(stores,accounts,journals);
    }
    @Test void unknownAccountDoesNotPost() {
        assertThrows(IllegalArgumentException.class,()->service.post(1L,"GR",10L,"GR1","BAD","2110",BigDecimal.ONE));verifyNoInteractions(stores,accounts,journals);
    }
    @Test void trialBalanceNetsClearedAccountsInsteadOfReportingTurnover() {
        JournalService view=spy(service);
        var first=new JournalService.JournalView(1L,"GR","GR1",null,List.of(
            new JournalService.LineView("1200","Stock",new BigDecimal("100.00"),BigDecimal.ZERO),
            new JournalService.LineView("2110","GRNI",BigDecimal.ZERO,new BigDecimal("100.00"))));
        var second=new JournalService.JournalView(2L,"AP","INV1",null,List.of(
            new JournalService.LineView("2110","GRNI",new BigDecimal("100.00"),BigDecimal.ZERO),
            new JournalService.LineView("2100","AP",BigDecimal.ZERO,new BigDecimal("100.00"))));
        doReturn(List.of(first,second)).when(view).list(1L);
        var result=view.trialBalance(1L);var cleared=result.stream().filter(b->b.accountCode().equals("2110")).findFirst().orElseThrow();
        assertEquals(0,cleared.debit().signum());assertEquals(0,cleared.credit().signum());
        assertEquals(0,result.stream().map(JournalService.BalanceView::debit).reduce(BigDecimal.ZERO,BigDecimal::add)
            .compareTo(result.stream().map(JournalService.BalanceView::credit).reduce(BigDecimal.ZERO,BigDecimal::add)));
    }
}
