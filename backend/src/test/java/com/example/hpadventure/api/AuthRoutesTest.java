package com.example.hpadventure.api;

import com.example.hpadventure.config.SemicolonSeparatedPairs;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthRoutesTest {

    private static Map<String, String> parse(String config) {
        return SemicolonSeparatedPairs.from(config).toMap();
    }

    @Test
    void validConfig() {
        AuthRoutes auth = new AuthRoutes(parse("anna:secret1,tom:secret2,lisa:magic3"));
        
        assertTrue(auth.isEnabled());
        
        var result1 = auth.validatePassword("secret1");
        assertTrue(result1.isValid());
        assertEquals("anna", result1.user());
        
        var result2 = auth.validatePassword("secret2");
        assertTrue(result2.isValid());
        assertEquals("tom", result2.user());
        
        var result3 = auth.validatePassword("magic3");
        assertTrue(result3.isValid());
        assertEquals("lisa", result3.user());
        
        assertFalse(auth.validatePassword("wrongpassword").isValid());
        assertFalse(auth.validatePassword(null).isValid());
        assertFalse(auth.validatePassword("").isValid());
    }

    @Test
    void emptyMap() {
        AuthRoutes auth = new AuthRoutes(Map.of());
        
        assertFalse(auth.isEnabled());
        assertFalse(auth.validatePassword("anypassword").isValid());
    }

    @Test
    void validateEndpoint_noAuthConfigured() {
        AuthRoutes auth = new AuthRoutes(Map.of());
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/validate");
            assertEquals(200, response.code());
        });
    }

    @Test
    void validateEndpoint_validPassword() {
        AuthRoutes auth = new AuthRoutes(Map.of("testuser", "testpass"));
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
        AuthRoutes auth = new AuthRoutes(Map.of("testuser", "testpass"));
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
        AuthRoutes auth = new AuthRoutes(Map.of("testuser", "testpass"));
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/validate");
            assertEquals(401, response.code());
        });
    }

    @Test
    void middleware_blocksUnauthenticated() {
        AuthRoutes auth = new AuthRoutes(Map.of("testuser", "testpass"));
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
        AuthRoutes auth = new AuthRoutes(Map.of());
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
