package com.oddlycoder.facep2pv2.bean;

import com.oddlycoder.facep2pv2.entity.User;
import com.oddlycoder.facep2pv2.service.UserService;
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
public class DashboardBean implements Serializable {

    @Inject
    private UserService userService;

    private User currentUser;
    private List<User> onlineUsers;

    @PostConstruct
    public void init() {
        HttpSession session = (HttpSession)
            FacesContext.getCurrentInstance().getExternalContext().getSession(false);
        currentUser = (User) session.getAttribute("currentUser");
        refreshOnlineUsers();
    }

    public void refreshOnlineUsers() {
        onlineUsers = userService.getOnlineUsers().stream()
                .filter(u -> !u.getUsername().equals(currentUser.getUsername()))
                .toList();
    }

    public User getCurrentUser() { return currentUser; }
    public List<User> getOnlineUsers() { return onlineUsers; }
}
