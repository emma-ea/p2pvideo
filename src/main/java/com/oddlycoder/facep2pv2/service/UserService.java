package com.oddlycoder.facep2pv2.service;

import com.oddlycoder.facep2pv2.entity.User;
import com.oddlycoder.facep2pv2.entity.UserStatus;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Stateless
public class UserService {

    @PersistenceContext(unitName = "p2pvideoPU")
    private EntityManager em;

    @Inject
    private PasswordUtil passwordUtil;

    @Transactional
    public User register(String username, String email, String rawPassword) {
        if (findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordUtil.hash(rawPassword));
        user.setStatus(UserStatus.OFFLINE);
        em.persist(user);
        return user;
    }

    public Optional<User> authenticate(String username, String rawPassword) {
        return findByUsername(username)
                .filter(u -> passwordUtil.verify(rawPassword, u.getPasswordHash()));
    }

    public Optional<User> findByUsername(String username) {
        TypedQuery<User> q = em.createQuery(
                "SELECT u FROM User u WHERE u.username = :u", User.class);
        q.setParameter("u", username);
        return q.getResultStream().findFirst();
    }

    public Optional<User> findByEmail(String email) {
        TypedQuery<User> q = em.createQuery(
                "SELECT u FROM User u WHERE u.email = :e", User.class);
        q.setParameter("e", email);
        return q.getResultStream().findFirst();
    }

    @Transactional
    public void setStatus(String username, UserStatus status) {
        findByUsername(username).ifPresent(u -> {
            u.setStatus(status);
            if (status == UserStatus.OFFLINE) {
                u.setLastSeen(new java.util.Date());
            }
        });
    }

    @Transactional
    public void updateProfile(String username, String displayName, String avatarUrl) {
        findByUsername(username).ifPresent(u -> {
            u.setDisplayName(displayName);
            u.setAvatarUrl(avatarUrl);
        });
    }

    public List<User> getOnlineUsers() {
        return em.createQuery(
                        "SELECT u FROM User u WHERE u.status <> :status ORDER BY u.username",
                        User.class)
                .setParameter("status", UserStatus.OFFLINE)
                .getResultList();
    }

    public List<Map<String, String>> getOnlineUserSummaries() {
        return getOnlineUsers().stream()
                .map(u -> Map.of(
                        "username", u.getUsername(),
                        "displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                        "status", u.getStatus().name()
                ))
                .toList();
    }
}
