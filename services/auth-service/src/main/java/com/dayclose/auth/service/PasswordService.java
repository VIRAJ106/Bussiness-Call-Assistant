package com.dayclose.auth.service;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.stereotype.Service;

/**
 * Password hashing service using Argon2id (ADR-0002).
 */
@Service
public class PasswordService {

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    public String hash(char[] password) {
        try {
            // iterations = 3, memory = 65536 KB (64MB), parallelism = 1
            return argon2.hash(3, 65536, 1, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    public String hash(String password) {
        return hash(password.toCharArray());
    }

    public boolean verify(String hash, char[] password) {
        try {
            return argon2.verify(hash, password);
        } finally {
            argon2.wipeArray(password);
        }
    }

    public boolean verify(String hash, String password) {
        return verify(hash, password.toCharArray());
    }
}
