package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;
import com.example.hpadventure.clients.AnthropicClient;
import com.example.hpadventure.parsing.CompletionParser;
import com.example.hpadventure.parsing.ItemParser;
import com.example.hpadventure.parsing.MarkerCleaner;
import com.example.hpadventure.parsing.OptionsParser;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class StoryService {
    private static final int STORY_MAX_TOKENS = 500;

    private final AnthropicClient anthropicClient;
    private final PromptBuilder promptBuilder;
    private final ItemParser itemParser;
    private final CompletionParser completionParser;
    private final OptionsParser optionsParser;
    private final MarkerCleaner markerCleaner;
    private final TitleService titleService;
    private final SummaryService summaryService;
    private final Clock clock;

    public StoryService(
        AnthropicClient anthropicClient,
        PromptBuilder promptBuilder,
        ItemParser itemParser,
        CompletionParser completionParser,
        OptionsParser optionsParser,
        MarkerCleaner markerCleaner,
        TitleService titleService,
        SummaryService summaryService,
        Clock clock
    ) {
        this.anthropicClient = anthropicClient;
        this.promptBuilder = promptBuilder;
        this.itemParser = itemParser;
        this.completionParser = completionParser;
        this.optionsParser = optionsParser;
        this.markerCleaner = markerCleaner;
        this.titleService = titleService;
        this.summaryService = summaryService;
        this.clock = clock;
    }

    public Dtos.Assistant nextTurn(Dtos.StoryRequest request) {
        List<Dtos.ChatMessage> history = request == null || request.conversationHistory() == null
            ? List.of()
            : request.conversationHistory();

        List<AnthropicClient.Message> messages = new ArrayList<>();
        for (Dtos.ChatMessage message : history) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            messages.add(new AnthropicClient.Message(message.role(), message.content()));
        }

        String action = request.action().trim();
        messages.add(new AnthropicClient.Message("user", action));

        String systemPrompt = promptBuilder.build(request.player());
        String rawStory = anthropicClient.createMessage(systemPrompt, messages, STORY_MAX_TOKENS);

        List<Dtos.Item> newItems = itemParser.parse(rawStory);
        boolean completed = completionParser.isComplete(rawStory);
        List<String> suggestedActions = optionsParser.parse(rawStory);
        String cleanStory = markerCleaner.strip(rawStory);

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
        return new Dtos.Assistant(cleanStory, suggestedActions, newItems, adventure, null);
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
}
