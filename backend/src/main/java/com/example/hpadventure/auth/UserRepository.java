package com.example.hpadventure.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class UserRepository {

    private final Map<String, String> passwordToUser;

    public UserRepository(Map<String, String> userToPassword) {
        this.passwordToUser = invertMap(userToPassword);
    }

    private static Map<String, String> invertMap(Map<String, String> map) {
        var result = new HashMap<String, String>();
        map.forEach((key, value) -> result.put(value, key));
        return result;
    }

    public int userCount() {
        return passwordToUser.size();
    }

    public Optional<String> authenticate(String password) {
        if (password == null || password.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(passwordToUser.get(password));
    }
}
