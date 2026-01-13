package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;
import com.example.hpadventure.providers.ImageProvider;
import com.example.hpadventure.providers.TextProvider;
import com.example.hpadventure.parsing.CompletionParser;
import com.example.hpadventure.parsing.MarkdownSanitizer;
import com.example.hpadventure.parsing.MarkerCleaner;
import com.example.hpadventure.parsing.OptionsParser;
import com.example.hpadventure.parsing.SceneParser;
import com.example.hpadventure.parsing.StreamMarkerFilter;
import com.example.hpadventure.services.StoryStreamHandler.StreamResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class StoryService implements StoryHandler, StoryStreamHandler {
    private static final int STORY_MAX_TOKENS = 500;
    private static final int STORY_ARC_TOTAL_STEPS = 15;

    private final TextProvider textProvider;
    private final PromptBuilder promptBuilder;
    private final CompletionParser completionParser;
    private final OptionsParser optionsParser;
    private final SceneParser sceneParser;
    private final MarkerCleaner markerCleaner;
    private final TitleService titleService;
    private final SummaryService summaryService;
    private final ImagePromptService imagePromptService;
    private final ImageProvider imageProvider;
    private final Clock clock;

    public StoryService(
        TextProvider textProvider,
        PromptBuilder promptBuilder,
        CompletionParser completionParser,
        OptionsParser optionsParser,
        SceneParser sceneParser,
        MarkerCleaner markerCleaner,
        TitleService titleService,
        SummaryService summaryService,
        ImagePromptService imagePromptService,
        ImageProvider imageProvider,
        Clock clock
    ) {
        this.textProvider = textProvider;
        this.promptBuilder = promptBuilder;
        this.completionParser = completionParser;
        this.optionsParser = optionsParser;
        this.sceneParser = sceneParser;
        this.markerCleaner = markerCleaner;
        this.titleService = titleService;
        this.summaryService = summaryService;
        this.imagePromptService = imagePromptService;
        this.imageProvider = imageProvider;
        this.clock = clock;
    }

    public Dtos.Assistant nextTurn(Dtos.StoryRequest request) {
        StoryContext context = buildStoryContext(request);
        String rawStory = textProvider.createMessage(context.systemPrompt(), context.messages(), STORY_MAX_TOKENS);
        StreamResult draft = buildAssistantDraft(request, context.history(), rawStory);
        Dtos.Image image = generateImage(draft.imagePrompt());
        return attachImage(draft.assistant(), image);
    }

    @Override
    public StreamResult streamTurn(Dtos.StoryRequest request, Consumer<String> onDelta) {
        StoryContext context = buildStoryContext(request);
        StringBuilder rawStory = new StringBuilder();
        StreamMarkerFilter markerFilter = new StreamMarkerFilter();
        MarkdownSanitizer markdownSanitizer = new MarkdownSanitizer();
        textProvider.streamMessage(context.systemPrompt(), context.messages(), STORY_MAX_TOKENS, delta -> {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            rawStory.append(delta);
            String cleaned = markerFilter.apply(delta);
            String sanitized = markdownSanitizer.strip(cleaned);
            if (!sanitized.isEmpty()) {
                onDelta.accept(sanitized);
            }
        });
        return buildAssistantDraft(request, context.history(), rawStory.toString());
    }

    @Override
    public Dtos.Image generateImage(String imagePrompt) {
        if (!imageProvider.isEnabled()) {
            return new Dtos.Image("text/plain", "disabled", null);
        }
        ImageProvider.ImageResult imageResult = imageProvider.generateImage(imagePrompt);
        return new Dtos.Image(imageResult.mimeType(), imageResult.base64(), imagePrompt);
    }

    private StoryContext buildStoryContext(Dtos.StoryRequest request) {
        List<Dtos.ChatMessage> history = request == null || request.conversationHistory() == null
            ? List.of()
            : request.conversationHistory();

        List<TextProvider.Message> messages = new ArrayList<>();
        for (Dtos.ChatMessage message : history) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            messages.add(new TextProvider.Message(message.role(), message.content()));
        }

        String action = request.action().trim();
        messages.add(new TextProvider.Message("user", action));
        int arcStep = storyArcStep(history);
        String systemPrompt = promptBuilder.build(request.player(), arcStep);

        return new StoryContext(history, messages, systemPrompt);
    }

    private StreamResult buildAssistantDraft(Dtos.StoryRequest request, List<Dtos.ChatMessage> history, String rawStory) {
        boolean completed = completionParser.isComplete(rawStory);
        List<String> suggestedActions = optionsParser.parse(rawStory);
        String scene = sceneParser.parse(rawStory);
        MarkdownSanitizer markdownSanitizer = new MarkdownSanitizer();
        String cleanStory = markdownSanitizer.strip(markerCleaner.strip(rawStory));
        String imagePrompt = imagePromptService.buildPrompt(scene, cleanStory);

        Instant now = Instant.now(clock);
        String adventureTitle = request.currentAdventure() != null ? request.currentAdventure().title() : null;
        List<String> assistantMessages = collectAssistantMessages(history, cleanStory);
        if (adventureTitle == null && assistantMessages.size() >= 2) {
            String generatedTitle = titleService.generateTitle(assistantMessages.subList(0, 2));
            if (generatedTitle != null && !generatedTitle.isBlank()) {
                adventureTitle = generatedTitle;
            }
        }

        String summary = null;
        String completedAt = null;
        if (completed) {
            List<Dtos.ChatMessage> summaryHistory = new ArrayList<>(history);
            summaryHistory.add(new Dtos.ChatMessage("assistant", cleanStory));
            summary = summaryService.generateSummary(summaryHistory);
            completedAt = now.toString();
        }

        Dtos.Adventure adventure = new Dtos.Adventure(adventureTitle, completed, summary, completedAt);
        Dtos.Assistant assistant = new Dtos.Assistant(cleanStory, suggestedActions, adventure, null);
        return new StreamResult(assistant, imagePrompt);
    }

    private Dtos.Assistant attachImage(Dtos.Assistant assistant, Dtos.Image image) {
        return new Dtos.Assistant(
            assistant.storyText(),
            assistant.suggestedActions(),
            assistant.adventure(),
            image
        );
    }

    private List<String> collectAssistantMessages(List<Dtos.ChatMessage> history, String latestStory) {
        List<String> assistantMessages = new ArrayList<>();
        if (history != null) {
            for (Dtos.ChatMessage message : history) {
                if (message != null && "assistant".equals(message.role()) && message.content() != null) {
                    assistantMessages.add(message.content());
                }
            }
        }
        if (latestStory != null && !latestStory.isBlank()) {
            assistantMessages.add(latestStory);
        }
        return assistantMessages;
    }

    private int storyArcStep(List<Dtos.ChatMessage> history) {
        int completedTurns = 0;
        if (history != null) {
            for (Dtos.ChatMessage message : history) {
                if (message != null && "assistant".equals(message.role())) {
                    completedTurns += 1;
                }
            }
        }
        int step = completedTurns + 1;
        if (step < 1) {
            return 1;
        }
        return Math.min(step, STORY_ARC_TOTAL_STEPS);
    }

    private record StoryContext(
        List<Dtos.ChatMessage> history,
        List<TextProvider.Message> messages,
        String systemPrompt
    ) {
    }
}
