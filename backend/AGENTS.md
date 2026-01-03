# HP Adventure Backend - Developer Guide

> Java 21 + Javalin backend for the HP Adventure game.

## Quick Start

```bash
./gradlew run              # Start server (port 7070)
./gradlew test             # Run unit tests
./gradlew shadowJar        # Build fat JAR
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                              App.java                               │
│                    Entry point, wiring, startup                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  api/         │         │  services/      │         │  config/        │
│  HTTP routes  │────────▶│  Business logic │         │  RateLimiter    │
└───────────────┘         └─────────────────┘         └─────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
            │  parsing/   │ │  providers/ │ │  Dtos.java  │
            │  LLM output │ │  External   │ │  Records    │
            │  parsing    │ │  APIs       │ │             │
            └─────────────┘ └─────────────┘ └─────────────┘
```

## Package Structure

```
src/main/java/com/example/hpadventure/
├── App.java                         # Entry, DI wiring, startup logging
├── api/
│   ├── Dtos.java                    # Request/response records
│   ├── HealthRoutes.java            # GET /health
│   ├── AuthRoutes.java              # POST /api/auth/validate
│   ├── StoryRoutes.java             # POST /api/story, /api/story/stream
│   └── TtsRoutes.java               # POST /api/tts
├── services/
│   ├── StoryService.java            # Main orchestration (implements StoryHandler)
│   ├── StoryHandler.java            # Interface for testability
│   ├── StoryStreamHandler.java      # SSE streaming interface
│   ├── PromptBuilder.java           # System prompt construction
│   ├── TitleService.java            # Adventure title generation
│   ├── SummaryService.java          # Adventure summary generation
│   ├── ImagePromptService.java      # Scene → image prompt
│   ├── TtsService.java              # TTS orchestration
│   ├── TtsHandler.java              # TTS interface
│   └── UpstreamException.java       # Provider error wrapper
├── parsing/
│   ├── CompletionParser.java        # Detect [ABSCHLUSS]
│   ├── OptionsParser.java           # Extract [OPTION: ...]
│   ├── ItemParser.java              # Extract [GEGENSTAND: name | desc]
│   ├── SceneParser.java             # Extract [SZENE: ...]
│   ├── MarkerCleaner.java           # Strip all markers from final text
│   ├── StreamMarkerFilter.java      # Filter markers during SSE streaming
│   └── MarkdownSanitizer.java       # Strip *, _, ` from text
├── providers/
│   ├── TextProvider.java            # Interface
│   ├── TextProviderFactory.java     # Creates provider from env
│   ├── OpenRouterTextProvider.java  # OpenRouter /v1/chat/completions
│   ├── AnthropicTextProvider.java   # Anthropic /v1/messages
│   ├── ImageProvider.java           # Interface
│   ├── ImageProviderFactory.java    # Creates provider from env
│   ├── OpenRouterImageProvider.java # OpenRouter image models
│   ├── OpenAiImageProvider.java     # OpenAI /v1/images/generations
│   ├── PlaceholderImageProvider.java# Fallback when disabled
│   ├── SpeechProvider.java          # Interface
│   ├── SpeechProviderFactory.java   # Creates provider from env
│   └── ElevenLabsSpeechProvider.java# ElevenLabs TTS
└── config/
    └── RateLimiter.java             # In-memory sliding window
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | No | Health check |
| `POST` | `/api/auth/validate` | No | Validate password |
| `POST` | `/api/story` | Yes | Generate story turn (JSON) |
| `POST` | `/api/story/stream` | Yes | Generate story turn (SSE) |
| `POST` | `/api/tts` | Yes | Text-to-speech |

### Request/Response (Dtos.java)

```java
// StoryRequest
record StoryRequest(Player player, CurrentAdventure currentAdventure, 
                    List<ChatMessage> conversationHistory, String action)

// StoryResponse  
record StoryResponse(Assistant assistant)
record Assistant(String storyText, List<String> suggestedActions, 
                 List<Item> newItems, Adventure adventure, Image image)
```

### SSE Events (streaming)

| Event | Data | Description |
|-------|------|-------------|
| `delta` | `{"text":"..."}` | Story chunk (markers filtered) |
| `final_text` | `{"text":"..."}` | Complete text + options |
| `image` | `{"mimeType":"...","base64":"...","prompt":"..."}` | Generated image |
| `image_error` | `{"error":"..."}` | Image generation failed |
| `done` | | Stream complete |

## Environment Variables

### Text Generation (at least one required)

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENROUTER_API_KEY` | | Primary provider |
| `ANTHROPIC_API_KEY` | | Fallback provider |

### Image Generation

| Variable | Default | Description |
|----------|---------|-------------|
| `IMAGE_PROVIDER` | auto | Force `openai` or `openrouter` |
| `OPENAI_API_KEY` | | Enable OpenAI images |

### Text-to-Speech

| Variable | Default | Description |
|----------|---------|-------------|
| `ELEVENLABS_API_KEY` | | Enable TTS |

### App Config

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `7070` | Server port |
| `RATE_LIMIT_PER_MINUTE` | `2` | 0 = disabled |
| `APP_PASSWORDS` | | `name:pass,name2:pass2` |

## LLM Output Markers

The LLM responses hopefully include special markers that are parsed and stripped:

| Marker | Parser | Purpose |
|--------|--------|---------|
| `[OPTION: ...]` | `OptionsParser` | Suggested player actions |
| `[SZENE: ...]` | `SceneParser` | Image generation prompt |
| `[GEGENSTAND: name \| desc]` | `ItemParser` | New inventory item |
| `[ABSCHLUSS]` | `CompletionParser` | Adventure complete |

- `StreamMarkerFilter` strips markers during SSE streaming
- `MarkerCleaner` strips markers from final JSON response
- `MarkdownSanitizer` strips `*`, `_`, `` ` `` from all text


## Testing

### Unit Tests
```bash
./gradlew test
```

### Smoke Tests (real APIs)
```bash
ANTHROPIC_API_KEY=... ./gradlew test --tests "AnthropicTextProviderSmokeTest"
OPENROUTER_API_KEY=... ./gradlew test --tests "OpenRouterTextProviderSmokeTest"
```

## Running Locally
Use tmux to not block your terminal during server runs.

```bash
./gradlew run   # Starts on port 7070
```

**Important**: Set `IMAGE_PROVIDER=openai` for local development - OpenRouter image models are more expensive. Required env vars:
- `OPENROUTER_API_KEY` - text generation
- `OPENAI_API_KEY` + `IMAGE_PROVIDER=openai` - image generation
