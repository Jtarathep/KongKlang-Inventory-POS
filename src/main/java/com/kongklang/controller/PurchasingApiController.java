package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/purchasing")
public class PurchasingApiController {
    private final AccessService access;
    private final PurchasingService purchasing;
    public PurchasingApiController(AccessService access,PurchasingService purchasing) {this.access=access;this.purchasing=purchasing;}
    private AccessService.Actor read(HttpSession session) {return access.require(session,"OWNER","ADMIN","PURCHASING","WAREHOUSE","ACCOUNTING");}
    private AccessService.Actor buy(HttpSession session) {return access.require(session,"OWNER","ADMIN","PURCHASING");}
    @GetMapping("/suppliers") public List<PurchasingService.SupplierView> suppliers(HttpSession session) {return purchasing.suppliers(read(session).storeId());}
    @PostMapping("/suppliers") public PurchasingService.SupplierView supplier(@RequestBody PurchasingService.SupplierInput input,HttpSession session) {return purchasing.saveSupplier(buy(session),null,input);}
    @PutMapping("/suppliers/{id}") public PurchasingService.SupplierView edit(@PathVariable Long id,@RequestBody PurchasingService.SupplierInput input,HttpSession session) {return purchasing.saveSupplier(buy(session),id,input);}
    @GetMapping("/orders") public List<PurchasingService.OrderView> orders(HttpSession session) {return purchasing.orders(read(session).storeId());}
    @GetMapping("/orders/{id}") public PurchasingService.OrderView order(@PathVariable Long id,HttpSession session) {return purchasing.order(read(session).storeId(),id);}
    @PostMapping("/orders") public PurchasingService.OrderView order(@RequestBody PurchasingService.OrderInput input,HttpSession session) {return purchasing.createOrder(buy(session),input);}
    @PostMapping("/orders/{id}/cancel") public PurchasingService.OrderView cancel(@PathVariable Long id,HttpSession session) {return purchasing.cancel(buy(session),id);}
    @GetMapping("/receipts") public List<PurchasingService.ReceiptView> receipts(HttpSession session) {return purchasing.receipts(read(session).storeId());}
    @PostMapping("/orders/{id}/receipts") public PurchasingService.ReceiptView receive(@PathVariable Long id,@RequestBody PurchasingService.ReceiptInput input,HttpSession session) {return purchasing.receive(access.require(session,"OWNER","ADMIN","WAREHOUSE"),id,input);}
}
