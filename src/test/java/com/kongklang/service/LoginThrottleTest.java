package com.kongklang.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LoginThrottleTest {
    @Test void stopsRepeatedAttemptsAcrossDifferentAddressesAndCaseVariants(){
        var t=new LoginThrottle();for(int i=0;i<10;i++)assertTrue(t.allow("test-"+i,i%2==0?"Cashier":"cashier"));
        assertFalse(t.allow("another-address","CASHIER"));assertTrue(t.allow("another-address","other-user"));
    }
    @Test void limitsManyAccountsFromOneAddress(){
        var t=new LoginThrottle();for(int i=0;i<30;i++)assertTrue(t.allow("single-address","test-user-"+i));assertFalse(t.allow("single-address","different-account"));
    }
}
