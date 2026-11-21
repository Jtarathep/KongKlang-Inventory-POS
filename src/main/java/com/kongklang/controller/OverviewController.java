package com.kongklang.controller;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.*;

@Controller
public class OverviewController {
    private final OverviewService overview;private final AccessService access;
    public OverviewController(OverviewService overview,AccessService access){this.overview=overview;this.access=access;}
    private OverviewService.Overview report(HttpSession session,LocalDate start,LocalDate end){
        var actor=access.require(session,"OWNER","ADMIN","ACCOUNTING");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));
        return overview.report(actor.storeId(),start==null?today.withDayOfMonth(1):start,end==null?today:end);
    }
    @GetMapping("/reports/overview") public String page(@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,HttpSession session,Model model){
        model.addAttribute("report",report(session,start,end));return "report-overview";
    }
    @GetMapping("/api/reports/overview") @ResponseBody public OverviewService.Overview api(@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end,HttpSession session){return report(session,start,end);}
    @GetMapping(value="/reports/overview/stock.csv",produces="text/csv;charset=UTF-8") @ResponseBody
    public ResponseEntity<String> csv(HttpSession session){
        StringBuilder csv=new StringBuilder("\uFEFFรหัสสินค้า,ชื่อสินค้า,หมวดหมู่,ยอดสินค้า,ยอดล็อต,หมดอายุ,หมดอายุใน30วัน,มูลค่าล็อต,ยอดตรงกัน\r\n");
        for(var s:report(session,null,null).stock()) csv.append(cell(s.code())).append(',').append(cell(s.name())).append(',').append(cell(s.category())).append(',')
            .append(s.quantity()).append(',').append(s.lotQuantity()).append(',').append(s.expired()).append(',').append(s.expiring()).append(',').append(s.value()).append(',').append(s.consistent()).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=kongklang-stock.csv").body(csv.toString());
    }
    static String cell(String value){
        String s=value==null?"":value;
        String stripped=s.stripLeading();
        if(!stripped.isEmpty()&&"=+-@".indexOf(stripped.charAt(0))>=0||s.startsWith("\t")||s.startsWith("\r")||s.startsWith("\n"))s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
}
