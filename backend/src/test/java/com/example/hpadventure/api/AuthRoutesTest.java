package com.example.hpadventure.api;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRoutesTest {

    @Test
    void parsePasswords_validConfig() {
        AuthRoutes auth = new AuthRoutes("anna:secret1,tom:secret2,lisa:magic3");
        
        assertTrue(auth.isEnabled());
        assertEquals("anna", auth.validatePassword("secret1"));
        assertEquals("tom", auth.validatePassword("secret2"));
        assertEquals("lisa", auth.validatePassword("magic3"));
        assertNull(auth.validatePassword("wrongpassword"));
        assertNull(auth.validatePassword(null));
        assertNull(auth.validatePassword(""));
    }

    @Test
    void parsePasswords_nullConfig() {
        AuthRoutes auth = new AuthRoutes(null);
        
        assertFalse(auth.isEnabled());
        assertNull(auth.validatePassword("anypassword"));
    }

    @Test
    void parsePasswords_emptyConfig() {
        AuthRoutes auth = new AuthRoutes("");
        
        assertFalse(auth.isEnabled());
    }

    @Test
    void parsePasswords_withWhitespace() {
        AuthRoutes auth = new AuthRoutes("  anna : secret1 , tom:secret2  ");
        
        assertTrue(auth.isEnabled());
        assertEquals("anna", auth.validatePassword("secret1"));
        assertEquals("tom", auth.validatePassword("secret2"));
    }

    @Test
    void parsePasswords_invalidEntries() {
        // Entries without colon or empty parts should be skipped
        AuthRoutes auth = new AuthRoutes("invalid,anna:secret1,:noname,nopassword:,valid:pwd");
        
        assertTrue(auth.isEnabled());
        assertEquals("anna", auth.validatePassword("secret1"));
        assertEquals("valid", auth.validatePassword("pwd"));
        assertNull(auth.validatePassword("invalid"));
    }

    @Test
    void validateEndpoint_noAuthConfigured() {
        AuthRoutes auth = new AuthRoutes(null);
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/validate");
            assertEquals(200, response.code());
        });
    }

    @Test
    void validateEndpoint_validPassword() {
        AuthRoutes auth = new AuthRoutes("testuser:testpass");
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.request("/api/auth/validate", builder -> 
                builder.post(okhttp3.RequestBody.create("", null))
                       .header("X-App-Password", "testpass"));
            assertEquals(200, response.code());
        });
    }

    @Test
    void validateEndpoint_invalidPassword() {
        AuthRoutes auth = new AuthRoutes("testuser:testpass");
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.request("/api/auth/validate", builder -> 
                builder.post(okhttp3.RequestBody.create("", null))
                       .header("X-App-Password", "wrongpass"));
            assertEquals(401, response.code());
        });
    }

    @Test
    void validateEndpoint_noPassword() {
        AuthRoutes auth = new AuthRoutes("testuser:testpass");
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/validate");
            assertEquals(401, response.code());
        });
    }

    @Test
    void middleware_blocksUnauthenticated() {
        AuthRoutes auth = new AuthRoutes("testuser:testpass");
        Javalin app = Javalin.create();
        app.before("/api/protected", auth.authMiddleware());
        app.get("/api/protected", ctx -> ctx.result("secret"));

        JavalinTest.test(app, (server, client) -> {
            // No password
            var response1 = client.get("/api/protected");
            assertEquals(401, response1.code());

            // Wrong password
            var response2 = client.request("/api/protected", builder -> 
                builder.get().header("X-App-Password", "wrong"));
            assertEquals(401, response2.code());

            // Correct password
            var response3 = client.request("/api/protected", builder -> 
                builder.get().header("X-App-Password", "testpass"));
            assertEquals(200, response3.code());
            assertEquals("secret", response3.body().string());
        });
    }

    @Test
    void middleware_allowsAllWhenDisabled() {
        AuthRoutes auth = new AuthRoutes(null);
        Javalin app = Javalin.create();
        app.before("/api/protected", auth.authMiddleware());
        app.get("/api/protected", ctx -> ctx.result("secret"));

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/api/protected");
            assertEquals(200, response.code());
            assertEquals("secret", response.body().string());
        });
    }
}
