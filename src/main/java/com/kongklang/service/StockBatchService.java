package com.kongklang.service;

import com.kongklang.entity.StockBatch;
import com.kongklang.repository.StockBatchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockBatchService {

    private final StockBatchRepository stockBatchRepository;

    public StockBatchService(
            StockBatchRepository stockBatchRepository) {

        this.stockBatchRepository = stockBatchRepository;
    }

    // ==============================
    // ดึงล็อตทั้งหมด
    // ==============================

    public List<StockBatch> getAllBatches(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("ไม่พบร้านค้าที่กำลังใช้งาน");
        }
        return stockBatchRepository.findAllByStoreId(storeId);
    }

    // ==============================
    // ดึงล็อตที่พร้อมขาย
    // ==============================

    public List<StockBatch> getAvailableBatches(
            String productCode, Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("ไม่พบร้านค้าที่กำลังใช้งาน");
        }
        return stockBatchRepository.findAvailableBatchesForSale(storeId, productCode);
    }

    // ==============================
    // ดึงล็อตตามรหัสสินค้า
    // ==============================

    public List<StockBatch> getBatchesByProductCode(
            String productCode, Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("ไม่พบร้านค้าที่กำลังใช้งาน");
        }
        return stockBatchRepository.findByStoreIdAndProductCodeOrderByReceivedAtAsc(
                storeId, productCode);
    }

    // ==============================
    // บันทึกล็อต
    // ==============================


}