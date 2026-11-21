package com.kongklang.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes={ProductApiController.class,CategoryApiController.class,OverviewController.class})
public class ProductValidationAdvice {
    @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class})
    public ResponseEntity<Map<String,Object>> invalid(RuntimeException error){return ResponseEntity.badRequest().body(Map.of("success",false,"message",error.getMessage()==null?"ข้อมูลไม่ถูกต้อง":error.getMessage()));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> conflict(RuntimeException error){return ResponseEntity.status(409).body(Map.of("success",false,"message","ข้อมูลซ้ำหรือยังถูกใช้งาน กรุณาตรวจสอบก่อนบันทึก"));}
}
