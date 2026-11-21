package com.kongklang.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RolePolicyTest {
    @Test void accountingCannotUseLegacySaleOrStockWrites() {
        assertFalse(RolePolicy.allows("ACCOUNTING","POST","/api/sales"));
        assertFalse(RolePolicy.allows("ACCOUNTING","POST","/stock/adjust"));
        assertTrue(RolePolicy.allows("ACCOUNTING","GET","/api/sales"));
    }
    @Test void warehouseCanReceiveButCannotManageProductMetadata() {
        assertTrue(RolePolicy.allows("WAREHOUSE","POST","/api/stock/in"));
        assertFalse(RolePolicy.allows("WAREHOUSE","PUT","/api/products/1"));
    }
    @Test void legacyGetDeleteEndpointIsStillProtected() {
        assertFalse(RolePolicy.allows("SALES","GET","/products/delete/1"));
        assertFalse(RolePolicy.allows("ADMIN","GET","/products/delete/1"));
        assertTrue(RolePolicy.allows("ADMIN","POST","/products/delete/1"));
    }
    @Test void salesAndPurchasingCannotOpenFinancialReports() {
        assertFalse(RolePolicy.allows("SALES","GET","/reports/profit"));
        assertFalse(RolePolicy.allows("PURCHASING","GET","/api/reports/today"));
        assertTrue(RolePolicy.allows("ACCOUNTING","GET","/api/reports/today"));
    }
    @Test void onlySystemOwnerCanManageOtherStores() {
        assertFalse(RolePolicy.allows("ADMIN","POST","/management/stores/create"));
        assertFalse(RolePolicy.allows("OWNER","POST","/management/stores/create"));
        assertTrue(RolePolicy.allows("SYSTEM_ADMIN","POST","/management/stores/create"));
        assertTrue(RolePolicy.allows("ADMIN","GET","/management/users"));
        assertFalse(RolePolicy.allows("SYSTEM_ADMIN","GET","/api/sales"));
        assertFalse(RolePolicy.allows("SALES","GET","/unknown-route"));
    }
}
