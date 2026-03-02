package com.oddlycoder.facep2pv2.service;

import com.oddlycoder.facep2pv2.entity.CallOutcome;
import com.oddlycoder.facep2pv2.entity.CallRecord;
import com.oddlycoder.facep2pv2.entity.User;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;

@Stateless
public class CallService {

    @PersistenceContext(unitName = "p2pvideoPU")
    private EntityManager em;

    @Transactional
    public CallRecord startCall(User caller, User callee) {
        CallRecord record = new CallRecord();
        record.setCaller(caller);
        record.setCallee(callee);
        record.setStartedAt(new Date());
        record.setOutcome(CallOutcome.FAILED); // default; updated on resolution
        em.persist(record);
        return record;
    }

    @Transactional
    public void markAnswered(Long callId) {
        CallRecord r = em.find(CallRecord.class, callId);
        if (r != null) {
            r.setAnsweredAt(new Date());
            r.setOutcome(CallOutcome.COMPLETED);
        }
    }

    @Transactional
    public void endCall(Long callId, CallOutcome outcome) {
        CallRecord r = em.find(CallRecord.class, callId);
        if (r != null) {
            r.setEndedAt(new Date());
            r.setOutcome(outcome);
        }
    }

    public List<CallRecord> getRecentCallsForUser(Long userId, int limit) {
        return em.createQuery(
                        "SELECT c FROM CallRecord c WHERE c.caller.id = :uid OR c.callee.id = :uid " +
                                "ORDER BY c.startedAt DESC", CallRecord.class)
                .setParameter("uid", userId)
                .setMaxResults(limit)
                .getResultList();
    }
}
