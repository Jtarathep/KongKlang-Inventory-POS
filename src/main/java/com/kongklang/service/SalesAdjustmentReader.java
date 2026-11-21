package com.kongklang.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SalesAdjustmentReader {
    private final JdbcTemplate jdbc;
    public SalesAdjustmentReader(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Adjustment(BigDecimal revenue,BigDecimal cost){}
    public record ProductReturn(String code,String name,int quantity,BigDecimal revenue,BigDecimal cost){}
    public Adjustment totals(Long store,LocalDateTime start,LocalDateTime end){return jdbc.queryForObject("select coalesce(sum(net_amount),0),coalesce(sum(restocked_cost),0) from sale_returns where store_id=? and created_at>=? and created_at<?",(r,n)->new Adjustment(r.getBigDecimal(1),r.getBigDecimal(2)),store,start,end);}
    public List<ProductReturn> products(Long store,LocalDateTime start,LocalDateTime end){return jdbc.query("select s.product_code,s.product_name,sum(i.quantity),sum(i.net_amount),sum(case when i.restock=1 then i.financial_cost else 0 end) from sale_return_items i join sale_returns r on r.id=i.return_id join sale_items s on s.id=i.sale_item_id where r.store_id=? and r.created_at>=? and r.created_at<? group by s.product_code,s.product_name",(r,n)->new ProductReturn(r.getString(1),r.getString(2),r.getInt(3),r.getBigDecimal(4),r.getBigDecimal(5)),store,start,end);}
    public List<Object[]> rows(Long store,LocalDateTime start,LocalDateTime end){return jdbc.query("select number,created_at,net_amount,restocked_cost from sale_returns where store_id=? and created_at>=? and created_at<? order by created_at desc",(r,n)->new Object[]{"คืน "+r.getString(1),r.getTimestamp(2).toLocalDateTime(),r.getBigDecimal(3).negate(),r.getBigDecimal(4).negate(),r.getBigDecimal(4).subtract(r.getBigDecimal(3))},store,start,end);}
}
