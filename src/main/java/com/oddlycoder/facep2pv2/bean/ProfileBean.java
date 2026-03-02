package com.oddlycoder.facep2pv2.bean;

import com.oddlycoder.facep2pv2.entity.User;
import com.oddlycoder.facep2pv2.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpSession;

import java.io.Serializable;

@Named
@ViewScoped
public class ProfileBean implements Serializable {

    @Inject
    private UserService userService;

    private User currentUser;
    private String displayName;
    private String avatarUrl;

    @PostConstruct
    public void init() {
        HttpSession session = (HttpSession)
            FacesContext.getCurrentInstance().getExternalContext().getSession(false);
        currentUser = (User) session.getAttribute("currentUser");
        displayName = currentUser.getDisplayName();
        avatarUrl   = currentUser.getAvatarUrl();
    }

    public void saveProfile() {
        userService.updateProfile(currentUser.getUsername(), displayName, avatarUrl);
        // Refresh session user
        userService.findByUsername(currentUser.getUsername()).ifPresent(u -> {
            HttpSession session = (HttpSession)
                FacesContext.getCurrentInstance().getExternalContext().getSession(false);
            session.setAttribute("currentUser", u);
            currentUser = u;
        });
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage("Profile updated successfully."));
    }

    public User getCurrentUser() { return currentUser; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
