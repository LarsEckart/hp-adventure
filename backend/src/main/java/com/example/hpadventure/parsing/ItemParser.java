package com.example.hpadventure.parsing;

import com.example.hpadventure.api.Dtos;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemParser {
    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[NEUER GEGENSTAND:\\s*([^|]+)\\s*\\|\\s*([^\\]]+)\\]");

    private final Clock clock;

    public ItemParser(Clock clock) {
        this.clock = clock;
    }

    public List<Dtos.Item> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Dtos.Item> items = new ArrayList<>();
        Matcher matcher = ITEM_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String description = matcher.group(2).trim();
            Instant foundAt = Instant.now(clock);
            items.add(new Dtos.Item(name, description, foundAt.toString()));
        }

        return items;
    }
}
