package com.example.hpadventure.auth;

import com.example.hpadventure.config.SemicolonSeparatedPairs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

    private static Map<String, String> parse(String config) {
        return SemicolonSeparatedPairs.from(config).toMap();
    }

    @Test
    void validConfig() {
        var repo = new UserRepository(parse("anna:secret1,tom:secret2,lisa:magic3"));
        
        assertTrue(repo.isEnabled());
        assertEquals(3, repo.userCount());
        
        assertEquals("anna", repo.authenticate("secret1").orElseThrow());
        assertEquals("tom", repo.authenticate("secret2").orElseThrow());
        assertEquals("lisa", repo.authenticate("magic3").orElseThrow());
        
        assertTrue(repo.authenticate("wrongpassword").isEmpty());
        assertTrue(repo.authenticate(null).isEmpty());
        assertTrue(repo.authenticate("").isEmpty());
    }

    @Test
    void emptyMap() {
        var repo = new UserRepository(Map.of());
        
        assertFalse(repo.isEnabled());
        assertEquals(0, repo.userCount());
        assertTrue(repo.authenticate("anypassword").isEmpty());
    }
}
