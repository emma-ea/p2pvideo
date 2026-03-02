package com.oddlycoder.facep2pv2.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "call_records")
public class CallRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caller_id")
    private User caller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "callee_id")
    private User callee;

    @Column(name = "started_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date startedAt;

    @Column(name = "answered_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date answeredAt;

    @Column(name = "ended_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallOutcome outcome;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getCaller() { return caller; }
    public void setCaller(User caller) { this.caller = caller; }

    public User getCallee() { return callee; }
    public void setCallee(User callee) { this.callee = callee; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }

    public Date getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Date answeredAt) { this.answeredAt = answeredAt; }

    public Date getEndedAt() { return endedAt; }
    public void setEndedAt(Date endedAt) { this.endedAt = endedAt; }

    public CallOutcome getOutcome() { return outcome; }
    public void setOutcome(CallOutcome outcome) { this.outcome = outcome; }
}
