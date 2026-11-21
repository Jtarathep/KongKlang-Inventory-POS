package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class StockTransactionService {
    private final StockBatchRepository batches;
    private final StockTransactionRepository transactions;

    public StockTransactionService(StockBatchRepository batches, StockTransactionRepository transactions) {
        this.batches = batches;
        this.transactions = transactions;
    }

    // All inventory writes now require an actor and durable request key through InventoryOperationService.
    public List<StockTransaction> getAllTransactions(Long storeId) {
        requireStore(storeId);
        return transactions.findAllByStoreIdOrderByCreatedAtDesc(storeId);
    }

    public List<StockBatch> getAvailableBatches(String code, Long storeId) {
        requireStore(storeId);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));
        return batches.findAvailableBatchesForSale(storeId, code).stream()
                .filter(b -> b.getExpiryDate() == null || !b.getExpiryDate().isBefore(today)).toList();
    }

    public List<StockBatch> getBatchesByProductCode(String code, Long storeId) {
        requireStore(storeId);
        return batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId, code);
    }

    private void requireStore(Long storeId) {
        if (storeId == null) throw new IllegalArgumentException("กรุณาระบุร้าน");
    }
}
