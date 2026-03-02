package com.oddlycoder.facep2pv2.controller;

import com.oddlycoder.facep2pv2.service.UserService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpSession;

@Named
@RequestScoped
public class AuthBean {

    @Inject
    private UserService userService;

    private String username;
    private String password;
    private String email;

    public String login() {
        return userService.authenticate(username, password)
                .map(user -> {
                    HttpSession session = (HttpSession)
                        FacesContext.getCurrentInstance().getExternalContext().getSession(true);
                    session.setAttribute("currentUser", user);
                    return "/dashboard.xhtml?faces-redirect=true";
                })
                .orElseGet(() -> {
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid credentials", null));
                    return null;
                });
    }

    public String register() {
        try {
            userService.register(username, email, password);
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage("Registration successful. Please log in."));
            return "/login.xhtml?faces-redirect=true";
        } catch (IllegalArgumentException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
            return null;
        }
    }

    public String logout() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        HttpSession session = (HttpSession) ctx.getExternalContext().getSession(false);
        if (session != null) session.invalidate();
        return "/login.xhtml?faces-redirect=true";
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
