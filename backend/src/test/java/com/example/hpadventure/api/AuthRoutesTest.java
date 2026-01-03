package com.example.hpadventure.api;

import com.example.hpadventure.auth.Users;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRoutesTest {

    @Test
    void validateEndpoint_validPassword() {
        AuthRoutes auth = new AuthRoutes(Users.parse("testuser:testpass"));
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
        AuthRoutes auth = new AuthRoutes(Users.parse("testuser:testpass"));
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
        AuthRoutes auth = new AuthRoutes(Users.parse("testuser:testpass"));
        Javalin app = Javalin.create();
        auth.register(app);

        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/validate");
            assertEquals(401, response.code());
        });
    }

    @Test
    void middleware_blocksUnauthenticated() {
        AuthRoutes auth = new AuthRoutes(Users.parse("testuser:testpass"));
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
}
