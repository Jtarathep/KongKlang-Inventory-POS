package com.kongklang.service;

import com.kongklang.entity.Store;
import com.kongklang.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;

@Service
public class StoreTaxService {
    private final StoreRepository stores;
    private final AuditTrail audit;
    public StoreTaxService(StoreRepository stores,AuditTrail audit){this.stores=stores;this.audit=audit;}
    public record Input(String mode, BigDecimal rate, boolean registered, String legalName,
                        String legalAddress, String taxId, String branchCode, long revision) {}
    @Transactional(readOnly=true)
    public Input read(Long storeId) { return view(stores.findById(storeId).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"))); }
    private Input view(Store s){return new Input(s.getTaxMode(),s.getVatRate(),s.isVatRegistered(),s.getLegalName(),s.getLegalAddress(),s.getTaxId(),s.getBranchCode(),s.getTaxRevision());}
    @Transactional
    public void save(AccessService.Actor actor,Input input) {
        if(actor==null||!Set.of("OWNER","ADMIN").contains(actor.role())) throw new IllegalArgumentException("ไม่มีสิทธิ์ตั้งค่าภาษี");
        if(input==null)throw new IllegalArgumentException("กรุณาระบุการตั้งค่าภาษี");
        SalePricing.TaxMode mode;
        try{mode=SalePricing.TaxMode.valueOf(input.mode());}catch(Exception e){throw new IllegalArgumentException("วิธีคิดภาษีไม่ถูกต้อง");}
        // Validate even when NONE so a dormant saved rate is safe to activate later.
        BigDecimal rate=new SalePricing.TaxRule(SalePricing.TaxMode.EXCLUSIVE,input.rate()).rate();
        String name=text(input.legalName(),255), address=text(input.legalAddress(),1000), id=text(input.taxId(),13), branch=text(input.branchCode(),5);
        if(!input.registered()&&mode!=SalePricing.TaxMode.NONE)throw new IllegalArgumentException("ร้านที่ยังไม่จด VAT ต้องเลือกไม่คิด VAT");
        if(input.registered()&&(name.isBlank()||address.isBlank()||!id.matches("[0-9]{13}")||!branch.matches("[0-9]{5}")))
            throw new IllegalArgumentException("กรุณากรอกชื่อและที่อยู่ผู้ประกอบการ เลขผู้เสียภาษี 13 หลัก และเลขสาขา 5 หลัก");
        Store store=stores.lockById(actor.storeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"));
        if(store.getTaxRevision()!=input.revision())throw new IllegalArgumentException("การตั้งค่าถูกเปลี่ยนแล้ว กรุณาเปิดหน้าใหม่ก่อนบันทึก");
        store.setTaxMode(mode.name());store.setVatRate(rate);store.setVatRegistered(input.registered());
        store.setLegalName(name);store.setLegalAddress(address);store.setTaxId(id);store.setBranchCode(branch);
        store.setTaxRevision(Math.addExact(store.getTaxRevision(),1));stores.save(store);
        audit.record(actor,"TAX_SETTINGS",mode.name()+" "+rate.toPlainString()+" revision "+store.getTaxRevision());
    }
    private static String text(String value,int max){String v=value==null?"":value.trim();if(v.length()>max)throw new IllegalArgumentException("ข้อมูลผู้ประกอบการยาวเกินกำหนด");return v;}
    public static SalePricing.TaxRule rule(Store store){
        SalePricing.TaxMode mode=SalePricing.TaxMode.valueOf(store.getTaxMode());
        if(mode==SalePricing.TaxMode.NONE)return SalePricing.TaxRule.none();
        if(!store.isVatRegistered())throw new IllegalStateException("การตั้งค่า VAT ของร้านไม่สอดคล้องกับสถานะจดทะเบียน");
        return new SalePricing.TaxRule(mode,store.getVatRate());
    }
}
