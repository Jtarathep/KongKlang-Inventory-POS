package com.kongklang.controller;
import com.kongklang.service.ProductPhotoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
@RestController
public class ProductPhotoController {
    private final ProductPhotoService photos;
    public ProductPhotoController(ProductPhotoService photos){this.photos=photos;}
    @GetMapping("/products/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id,HttpSession session){
        byte[] image=photos.read(id,session);
        String type="image/jpeg";
        if(image==null){type="image/svg+xml";image=("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"160\" height=\"160\" viewBox=\"0 0 160 160\"><rect width=\"160\" height=\"160\" rx=\"20\" fill=\"#f1f6fc\"/><g fill=\"none\" stroke=\"#7085a3\" stroke-width=\"4\" stroke-linejoin=\"round\"><path d=\"M45 59 80 40 115 59 115 101 80 121 45 101Z M45 59 80 79 115 59 M80 79V121 M62 50 98 69V84\"/></g></svg>").getBytes(StandardCharsets.UTF_8);}
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(type)).body(image);
    }
}
