package com.oddlycoder.facep2pv2.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class PasswordUtil {

    public String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public boolean verify(String rawPassword, String hashed) {
        return BCrypt.checkpw(rawPassword, hashed);
    }
}
