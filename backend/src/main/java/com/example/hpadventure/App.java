package com.example.hpadventure;

import com.example.hpadventure.api.Dtos;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;

import java.util.List;
import java.util.UUID;

public final class App {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        Javalin app = Javalin.create(config -> {
            ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/public/index.html");
        });

        app.get("/health", ctx -> ctx.result("ok"));
        app.post("/api/story", ctx -> {
            Dtos.StoryRequest request = ctx.bodyAsClass(Dtos.StoryRequest.class);
            String action = request == null ? null : request.action();

            if (action == null || action.isBlank()) {
                String requestId = UUID.randomUUID().toString();
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "action is required", requestId));
                return;
            }

            String playerName = "Du";
            if (request != null && request.player() != null && request.player().name() != null && !request.player().name().isBlank()) {
                playerName = request.player().name().trim();
            }

            String storyText = """
                %s triffst eine Entscheidung: "%s".
                Die Kerzen in der Großen Halle flackern, als ob Hogwarts deine Wahl bemerkt.

                Was tust du?
                """.formatted(playerName, action.trim());

            List<String> suggestedActions = List.of(
                "Leise zur Tür schleichen",
                "Den Zauberstab heben und Lumos wirken",
                "Professorin McGonagall suchen"
            );

            String currentTitle = request != null && request.currentAdventure() != null
                ? request.currentAdventure().title()
                : null;

            Dtos.Adventure adventure = new Dtos.Adventure(currentTitle, false, null, null);
            Dtos.Assistant assistant = new Dtos.Assistant(storyText, suggestedActions, List.of(), adventure, null);

            ctx.json(new Dtos.StoryResponse(assistant));
        });

        app.start(port);
    }

    private static Dtos.ErrorResponse errorResponse(String code, String message, String requestId) {
        return new Dtos.ErrorResponse(new Dtos.ErrorResponse.Error(code, message, requestId));
    }
}
