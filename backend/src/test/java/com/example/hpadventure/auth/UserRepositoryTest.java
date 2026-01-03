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
    void authenticatesValidPasswords() {
        var repo = new UserRepository(parse("anna:secret1,tom:secret2,lisa:magic3"));
        
        assertEquals(3, repo.userCount());
        
        assertEquals("anna", repo.authenticate("secret1").orElseThrow());
        assertEquals("tom", repo.authenticate("secret2").orElseThrow());
        assertEquals("lisa", repo.authenticate("magic3").orElseThrow());
    }

    @Test
    void rejectsInvalidPasswords() {
        var repo = new UserRepository(parse("anna:secret1"));
        
        assertTrue(repo.authenticate("wrongpassword").isEmpty());
        assertTrue(repo.authenticate(null).isEmpty());
        assertTrue(repo.authenticate("").isEmpty());
        assertTrue(repo.authenticate("   ").isEmpty());
    }
}
