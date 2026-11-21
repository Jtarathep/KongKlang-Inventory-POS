package com.kongklang.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity
@Table(name="gl_journal_lines")
public class GlJournalLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="journal_id",nullable=false)
    private GlJournal journal;
    public GlJournal getJournal() { return journal; }
    public void setJournal(GlJournal value) { this.journal = value; }
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="account_id",nullable=false)
    private GlAccount account;
    public GlAccount getAccount() { return account; }
    public void setAccount(GlAccount value) { this.account = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal debit;
    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal value) { this.debit = value; }
    @Column(nullable=false,precision=19,scale=2)
    private BigDecimal credit;
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal value) { this.credit = value; }
}
