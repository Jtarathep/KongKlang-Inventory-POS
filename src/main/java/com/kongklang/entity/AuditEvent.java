package com.kongklang.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Mapping also makes startup schema validation require the audit table. */
@Entity @Table(name="audit_logs")
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false,length=80) private String action;
    @Column(nullable=false) private String reference;
    @Column(nullable=false) private LocalDateTime createdAt;
}
