package com.example.hpadventure.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsersTest {

    @Test
    void parsesValidConfig() {
        var users = Users.parse("anna:secret1,tom:secret2,lisa:magic3");
        
        assertEquals(3, users.count());
        assertEquals("anna", users.findByPassword("secret1").orElseThrow());
        assertEquals("tom", users.findByPassword("secret2").orElseThrow());
        assertEquals("lisa", users.findByPassword("magic3").orElseThrow());
    }

    @Test
    void rejectsInvalidPasswords() {
        var users = Users.parse("anna:secret1");
        
        assertTrue(users.findByPassword("wrongpassword").isEmpty());
        assertTrue(users.findByPassword(null).isEmpty());
        assertTrue(users.findByPassword("").isEmpty());
        assertTrue(users.findByPassword("   ").isEmpty());
    }

    @Test
    void handlesNullConfig() {
        var users = Users.parse(null);
        assertEquals(0, users.count());
    }

    @Test
    void handlesEmptyConfig() {
        var users = Users.parse("");
        assertEquals(0, users.count());
    }

    @Test
    void trimsWhitespace() {
        var users = Users.parse("  anna : secret1 , tom:secret2  ");
        
        assertEquals(2, users.count());
        assertEquals("anna", users.findByPassword("secret1").orElseThrow());
        assertEquals("tom", users.findByPassword("secret2").orElseThrow());
    }

    @Test
    void skipsInvalidEntries() {
        var users = Users.parse("invalid,anna:secret1,:novalue,nokey:,valid:pwd");
        
        assertEquals(2, users.count());
        assertEquals("anna", users.findByPassword("secret1").orElseThrow());
        assertEquals("valid", users.findByPassword("pwd").orElseThrow());
    }

    @Test
    void allowsColonsInPassword() {
        var users = Users.parse("user:pass:with:colons");
        
        assertEquals(1, users.count());
        assertEquals("user", users.findByPassword("pass:with:colons").orElseThrow());
    }
}
