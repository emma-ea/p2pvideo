package com.oddlycoder.facep2pv2.controller;

import com.oddlycoder.facep2pv2.entity.CallRecord;
import com.oddlycoder.facep2pv2.entity.User;
import com.oddlycoder.facep2pv2.service.CallService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class CallHistoryBean implements Serializable {

    @Inject
    private CallService callService;

    private List<CallRecord> recentCalls;
    private User currentUser;

    @PostConstruct
    public void init() {
        HttpSession session = (HttpSession)
            FacesContext.getCurrentInstance().getExternalContext().getSession(false);
        currentUser = (User) session.getAttribute("currentUser");
        recentCalls = callService.getRecentCallsForUser(currentUser.getId(), 20);
    }

    public String formatDuration(CallRecord call) {
        if (call.getAnsweredAt() == null || call.getEndedAt() == null) {
            return "-";
        }
        long seconds = (call.getEndedAt().getTime() - call.getAnsweredAt().getTime()) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public List<CallRecord> getRecentCalls() { return recentCalls; }
    public User getCurrentUser() { return currentUser; }
}
