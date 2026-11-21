package com.kongklang.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class StoreContextService {

    public Long getCurrentStoreId(HttpSession session) {

        Object storeId = session.getAttribute("storeId");

        if (storeId == null) {
            return null;
        }

        if (storeId instanceof Long) {
            return (Long) storeId;
        }

        if (storeId instanceof Number) {
            return ((Number) storeId).longValue();
        }

        return Long.valueOf(storeId.toString());
    }

    public String getCurrentStoreCode(HttpSession session) {

        Object storeCode = session.getAttribute("storeCode");

        return storeCode == null
                ? null
                : storeCode.toString();
    }

    public String getCurrentStoreName(HttpSession session) {

        Object storeName = session.getAttribute("storeName");

        return storeName == null
                ? null
                : storeName.toString();
    }

    public boolean hasStore(HttpSession session) {
        return getCurrentStoreId(session) != null;
    }
}