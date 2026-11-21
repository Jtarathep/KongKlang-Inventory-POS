package com.kongklang.controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes={StockApiController.class,LegacyInventoryController.class})
public class InventoryExceptionAdvice {
    @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class})
    public ResponseEntity<Map<String,Object>> invalid(RuntimeException error){
        return ResponseEntity.badRequest().body(Map.of("success",false,"message",error.getMessage()==null?"ข้อมูลไม่ถูกต้อง":error.getMessage()));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String,Object>> malformed(Exception error){return ResponseEntity.badRequest().body(Map.of("success",false,"message","รูปแบบคำขอไม่ถูกต้อง กรุณาตรวจสอบข้อมูล"));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> conflict(RuntimeException error){return ResponseEntity.status(409).body(Map.of("success",false,"message","กรุณาตรวจสอบและส่งคำขอเดิมอีกครั้ง"));}
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> access(org.springframework.web.server.ResponseStatusException error){return ResponseEntity.status(error.getStatusCode()).body(Map.of("success",false,"message","กรุณาตรวจสอบบัญชีและสิทธิ์ของคุณ"));}
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String,Object>> uncertain(RuntimeException error){return ResponseEntity.status(500).body(Map.of("success",false,"message","ยังยืนยันผลไม่ได้ กรุณาเก็บคำขอเดิมและตรวจสอบอีกครั้ง"));}
}
