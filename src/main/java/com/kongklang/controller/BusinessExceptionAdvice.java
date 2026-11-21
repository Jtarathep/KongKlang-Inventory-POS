package com.kongklang.controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes={PurchasingApiController.class,AccountingApiController.class,ManualPayableController.class})
public class BusinessExceptionAdvice {
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,ArithmeticException.class})
    public ResponseEntity<Map<String,Object>> invalid(RuntimeException error) {
        return ResponseEntity.badRequest().body(Map.of("success",false,"message",error.getMessage()==null?"ข้อมูลไม่ถูกต้อง":error.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> duplicate(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success",false,"message","ข้อมูลซ้ำหรือข้อมูลอ้างอิงไม่ถูกต้อง กรุณาตรวจสอบรายการแล้วลองใหม่"));
    }
}
