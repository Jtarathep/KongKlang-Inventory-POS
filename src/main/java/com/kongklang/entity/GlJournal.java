package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="gl_journals", uniqueConstraints=@UniqueConstraint(name="uk_journal_source",columnNames={"store_id","source_type","source_id"}))
public class GlJournal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id",nullable=false)
    private Store store;
    public Store getStore() { return store; }
    public void setStore(Store value) { this.store = value; }
    @Column(nullable=false)
    private String sourceType;
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { this.sourceType = value; }
    @Column(nullable=false)
    private Long sourceId;
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long value) { this.sourceId = value; }
    @Column(nullable=false)
    private String reference;
    public String getReference() { return reference; }
    public void setReference(String value) { this.reference = value; }
    @Column(nullable=false)
    private LocalDateTime postedAt;
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime value) { this.postedAt = value; }
    @OneToMany(mappedBy="journal", cascade=CascadeType.ALL)
    private List<GlJournalLine> items = new ArrayList<>();
    public List<GlJournalLine> getItems() { return items; }
    public void setItems(List<GlJournalLine> value) { this.items = value; }
}
