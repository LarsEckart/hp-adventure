package com.example.hpadventure;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public final class App {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/public/index.html");
        });

        app.get("/health", ctx -> ctx.result("ok"));

        app.start(port);
    }
}
