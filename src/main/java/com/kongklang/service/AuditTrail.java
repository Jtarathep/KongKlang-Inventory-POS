package com.kongklang.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** Persisted in the business transaction; rollback also removes its audit event. */
@Service
public class AuditTrail {
    private final JdbcTemplate jdbc;
    public AuditTrail(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(AccessService.Actor actor,String action,String reference){
        jdbc.update("insert into audit_logs(store_id,user_id,action,reference,created_at) values(?,?,?,?,?)",
            actor.storeId(),actor.userId(),action,reference,LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
    }
    @Transactional(readOnly=true)
    public List<Event> recent(Long storeId){
        return jdbc.query("select id,user_id,action,reference,created_at from audit_logs where store_id=? order by id desc limit 200",
            (rs,n)->new Event(rs.getLong("id"),rs.getLong("user_id"),rs.getString("action"),rs.getString("reference"),rs.getTimestamp("created_at").toLocalDateTime()),storeId);
    }
    public record Event(Long id,Long userId,String action,String reference,LocalDateTime createdAt){}
}
