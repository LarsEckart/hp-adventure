# Development Plan — Rebuild Harry Potter Text-Adventure (CLI → Browser)

This document describes a detailed plan to rebuild the existing **German** Node.js CLI text adventure (Anthropic Claude story generation, streaming, inventory + adventure history mechanics) as a **browser application** with:

- **Frontend:** Elm 0.19 (UI + state + persistence)
- **Backend:** Java + Javalin (minimal “AI gateway/orchestrator” only)
- **Hosting:** Railway (free tier)
- **Persistence:** `localStorage` (frontend)
- **Images:** OpenAI **`gpt-image-1`** image generation **for every story response**

---

## Status (2026-01-01)

Milestones 0–2 are complete, plus service worker caching:

- Backend serves `/health` and calls Anthropic `/v1/messages` in `/api/story` (prompt builder + marker parsing + title/summary hooks).
- Backend parsing helpers + JUnit coverage (items, completion, options, marker stripping, prompt builder).
- OpenAI image generation is wired in (`gpt-image-1`), including scene parsing + prompt templating, and `/api/story` returns base64 images.
- Frontend now persists full player state: inventory, completed adventures, stats, and per-turn data.
- Inventory/history UI panels, completion handling, and stats are wired in Elm.
- Command shortcuts now work in the UI: `inventar`, `geschichte`, `aufgeben`, `start`.
- Story feed renders per-turn illustrations and persists them locally.
- Story feed now auto-scrolls to the latest turn.
- Service worker caching is in place for the app shell (`sw.js`).
- Offline UX is in place (banner + send disabled while offline).
- `POST /api/story` now has in-memory rate limiting (configurable via `RATE_LIMIT_PER_MINUTE`).
- Adventure `startedAt` timestamps are set client-side as ISO strings.
- Build script `frontend/build.sh` still compiles Elm and copies assets into `backend/src/main/resources/public`.
- Added backend StoryService integration coverage using OkHttp MockWebServer for Anthropic/OpenAI stubs (title + summary paths).
- API routes are now factored into `HealthRoutes` + `StoryRoutes`, with Javalin test coverage for happy path, validation, rate limiting, and upstream errors.
- Added Playwright E2E smoke test with mocked `/api/story` responses and stable `data-testid` hooks.
- Streaming responses (Milestone 6) are now implemented end-to-end (backend SSE + frontend streaming UI).
- Frontend now drops the last pending turn when a story request fails (stream or non-stream), so the feed doesn’t get stuck in a “writing…” state.

Next up: polish/optimizations if needed.

---

## 0) Guiding principles & scope

### Goals
- Preserve the current mechanics from `adventure.js`:
  - Player profile: name + house
  - Inventory with item acquisition markers
  - Current adventure session with conversation history
  - Completed adventures list with **title + summary + completion date**
  - Short, German, immersive narration; each assistant response ends with a question
- Browser UX:
  - **Scrollable story feed**
  - Each assistant response has a **generated illustration**
  - **Choice buttons** (suggested actions) + free text input
  - Works **offline for viewing history** (state loaded from `localStorage`)
- Clear separation:
  - Elm: UI, state transitions, persistence, offline viewing
  - Java: Anthropic + OpenAI API calls, parsing AI output, returning structured response

### Non-goals (initially)
- Multiplayer / server-side accounts
- Server-side persistence
- Perfect streaming in Elm on day 1 (we can add later as a milestone)

### Key constraints to account for early
- `localStorage` size is limited; images can be large.
  - Use `output_format` + `output_compression` + `quality` to reduce payload sizes via OpenAI image generation. ([platform.openai.com](https://platform.openai.com/docs/api-reference/images/generate))
- Backend must be stateless: every `/api/story` request includes the needed state/context.

---

## 1) Project structure (directories & files)

Recommended monorepo layout (single Railway service, backend serves the compiled frontend):

```text
hp-adventure-web/
  plan.md

  backend/
    build.gradle.kts
    settings.gradle.kts
    src/
      main/
        java/
          com/example/hpadventure/
            App.java
            config/
              Json.java
              Env.java
              Cors.java
              RateLimit.java
            api/
              StoryRoutes.java
              HealthRoutes.java
              Dtos.java
              Errors.java
            domain/
              Player.java
              Item.java
              CompletedAdventure.java
              Stats.java
              ChatMessage.java
              StoryRequest.java
              StoryResponse.java
            services/
              PromptBuilder.java
              StoryService.java
              TitleService.java
              SummaryService.java
              ImagePromptService.java
            clients/
              AnthropicClient.java
              OpenAiImageClient.java
              HttpClient.java
            parsing/
              Markers.java
              ItemParser.java
              CompletionParser.java
              OptionsParser.java
              SceneParser.java
        resources/
          public/                 # compiled Elm assets copied here
            index.html
            elm.js
            styles.css
            app.js                # JS glue for ports + SW registration
            sw.js                 # service worker (optional but recommended)
    Dockerfile                    # optional (recommended for reproducible Railway builds)

  frontend/
    elm.json
    src/
      Main.elm
      Model.elm
      Msg.elm
      Update.elm
      View.elm
      View/
        Layout.elm
        StoryFeed.elm
        Inventory.elm
        History.elm
        Setup.elm
      Api.elm
      Storage.elm               # ports abstraction
      Codec.elm                 # JSON encode/decode
      Util.elm
    public/
      index.html                # template
      styles.css
      app.js                    # ports + SW registration
      sw.js
    build.sh                    # builds elm.js + copies to backend/resources/public

  .gitignore
  README.md
```

### Notes on this structure
- **Backend serves static files** from classpath `/public` and uses SPA fallback (`spaRoot`) so refreshes work. ([javalin.io](https://javalin.io/documentation?utm_source=openai))  
- Frontend is built into `elm.js` and copied into `backend/src/main/resources/public`.
- `app.js` is tiny JS glue to:
  - Load state from `localStorage` as Elm flags
  - Persist state back to `localStorage` via ports
  - Register `sw.js` for offline loading (optional but recommended)

---

## 2) Backend (Java + Javalin) — implementation steps

### 2.1 Create the Javalin skeleton
1. **Gradle setup** (Java 21 recommended)
   - Dependencies:
     - `io.javalin:javalin`
     - Jackson (JSON)
     - OkHttp (HTTP client)
     - JUnit + WireMock (testing)
2. `App.java`:
   - Reads `PORT` from environment (Railway provides it)
   - `config.staticFiles.add(...)` to serve `/public` (compiled Elm output). ([javalin.io](https://javalin.io/documentation?utm_source=openai))
   - Configure SPA fallback via `config.spaRoot.addFile("/", "/public/index.html")` (or equivalent) so the Elm SPA works with refresh/navigation. ([javalin.io](https://javalin.io/documentation?utm_source=openai))
   - Register routes:
     - `GET /health`
     - `POST /api/story`

### 2.2 CORS + basic hardening (dev friendly, prod safe)
- In local development (frontend served separately), enable CORS (allow localhost origins) using Javalin’s CORS plugin. ([javalin.io](https://javalin.io/plugins/cors?utm_source=openai))
- Add minimal in-memory rate limiting (per IP, simple token-bucket or leaky bucket). This is important because your API keys are behind this service.

### 2.3 Port mechanics from `adventure.js` into backend services

Map existing functions into Java services/classes:

| CLI function (`adventure.js`) | Java equivalent | Purpose |
|---|---|---|
| `buildSystemPrompt(player)` | `PromptBuilder.build(player)` | System prompt includes player name/house, inventory, recent adventure summaries |
| `parseNewItems(response)` | `ItemParser.parse(response)` | Regex parse `[NEUER GEGENSTAND: Name \| Beschreibung]` |
| `isAdventureComplete(response)` | `CompletionParser.isComplete(response)` | Detect `[ABENTEUER ABGESCHLOSSEN]` |
| `generateTitle(history)` | `TitleService.generate(history)` | One-off call to Anthropic |
| `generateSummary(history)` | `SummaryService.generate(history)` | One-off call to Anthropic |
| `chat(history, player)` | `StoryService.nextTurn(player, history, action)` | Main story generation call |

### 2.4 Prompt format upgrades (to support choices + image scene extraction)

To reliably drive **choice buttons** and **image prompt extraction**, don’t rely on “implicit options” only. Keep the existing story rules, but add a **small, parseable metadata section** at the end of each assistant response.

**Add to system prompt (German):**
- Keep: `[NEUER GEGENSTAND: ...]`, `[ABENTEUER ABGESCHLOSSEN]`
- Add:
  - `[OPTION: ...]` repeated 2–3 times
  - `[SZENE: ...]` a single short visual description (what the image should depict)

Example expected assistant output:
```text
(Story paragraph 1...)

(Story paragraph 2...)

Was tust du?

[OPTION: Leise zur Tür schleichen]
[OPTION: Einen Zauber wirken (z.B. Lumos)]
[OPTION: Professorin McGonagall suchen]
[SZENE: Dunkler Hogwarts-Korridor bei Nacht, Kerzenlicht, flackernde Schatten, ein Schüler mit Zauberstab im Vordergrund]
```

**Why this matters**
- Elm can render the `[OPTION: ...]` entries as buttons.
- Backend can use `[SZENE: ...]` to generate consistent image prompts without extra LLM calls.

Fallback behavior:
- If options/scene markers are missing, backend falls back to heuristic extraction (e.g., last question paragraph for options; first paragraph for scene).

### 2.5 Implement `POST /api/story`

#### Core flow (backend is stateless)
1. Validate request (presence of `player`, `conversationHistory`, `action`; length limits).
2. Build Anthropic messages array:
   - Use `conversationHistory` from client (roles `user`/`assistant`)
   - Append the new `action` as a new `user` message
3. Call Anthropic to generate next story:
   - Model: keep the CLI model (e.g. `claude-haiku-4-5-20251001`)
   - `system`: from `PromptBuilder`
4. Parse the assistant text:
   - `newItems` via marker regex
   - `adventureCompleted` via marker
   - `suggestedActions` via `[OPTION: ...]`
   - `scene` via `[SZENE: ...]`
   - `displayText`: remove all markers for UI
   - `assistantTextForHistory`: store **cleaned story** (remove metadata markers); optionally keep item markers out of history too to reduce token noise
5. Title generation:
   - If request says current adventure title is missing and enough context exists (same heuristic as CLI), call `TitleService.generate()`
6. Summary generation:
   - If adventure just completed, call `SummaryService.generate()`
7. Image generation (always for assistant responses):
   - Derive image prompt from `scene` + a stable style template
   - Call OpenAI Images API with `model="gpt-image-1"`
8. Return a structured response with story text + image + deltas.

### 2.6 Anthropic client (Java)
Implement `AnthropicClient` with:
- `generateStory(systemPrompt, messages)`
- `generateTitle(messages)`
- `generateSummary(messages)`

Implementation approach:
- Use OkHttp to call Anthropic Messages API.
- Keep response parsing strict and defensive (Anthropic returns content blocks; extract concatenated text).

Optional later milestone: streaming
- Anthropic streaming uses SSE events; text arrives as `content_block_delta` with `text_delta`. ([docs.anthropic.com](https://docs.anthropic.com/de/docs/build-with-claude/streaming?utm_source=openai))  
- Elm cannot consume streaming HTTP via `Http` natively; if you want streaming UX, implement it via JS `fetch()` streaming + ports, or use SSE/WebSocket.

### 2.7 OpenAI image generation client (Java)
Implement `OpenAiImageClient.generate(prompt)`:
- Use OpenAI **Image API** `POST /v1/images/generations`
- Use `model: "gpt-image-1"`
- Use `output_format` + `output_compression` + `quality` to keep payload smaller (important for localStorage/offline history):
  - `output_format: "webp"` (or `"jpeg"`)
  - `output_compression: 60..85`
  - `quality: "low"` (or `"medium"` for better quality)
- Note: GPT image model sizes are `1024x1024`, `1536x1024`, `1024x1536`, or `auto`. ([platform.openai.com](https://platform.openai.com/docs/api-reference/images/generate))
- Response for GPT image models is **base64** (`data[0].b64_json`); `response_format` is not supported for GPT image models and they always return base64-encoded images. ([platform.openai.com](https://platform.openai.com/docs/api-reference/images/generate))
- OpenAI also supports `stream` and `partial_images` for GPT image models (optional later). ([platform.openai.com](https://platform.openai.com/docs/api-reference/images/generate))

### 2.8 Error handling & resilience
- Timeouts per upstream call:
  - Anthropic story: e.g., 30s
  - OpenAI image: e.g., 60s
- Return consistent API errors:
  - `400` invalid request
  - `429` rate limited
  - `502` upstream failure
- Include a `requestId` in responses to correlate logs.

---

## 3) Frontend (Elm 0.19) — implementation steps

### 3.1 Elm architecture (modules)
Recommended modules:

- `Main.elm`: `Browser.application` (or `Browser.element` if you keep routing minimal)
- `Model.elm`: all domain/UI types
- `Msg.elm`: messages
- `Update.elm`: state transitions + commands
- `View.elm`: top-level view
- `View/Layout.elm`: two-column layout primitives
- `View/StoryFeed.elm`: the scrolling feed of turns (text + image)
- `View/Setup.elm`: new player onboarding
- `View/Inventory.elm`: inventory list
- `View/History.elm`: completed adventures (title + summary)
- `Api.elm`: `/api/story` HTTP call + decoders
- `Codec.elm`: encode/decode for localStorage schema
- `Storage.elm`: ports wrapper (save/load/clear)
- `Util.elm`: helpers (ISO dates, string trimming, command parsing)

### 3.2 Elm data model (client-owned state)
Mirror the CLI state, but adapted for browser UI:

```elm
type alias Player =
    { name : String
    , houseName : String
    , inventory : List Item
    , completedAdventures : List CompletedAdventure
    , stats : Stats
    }

type alias CurrentAdventure =
    { title : Maybe String
    , startedAt : IsoString
    , turns : List Turn
    }

type alias Turn =
    { userAction : String
    , assistant : Maybe AssistantTurn -- Nothing while waiting
    , createdAt : IsoString
    }

type alias AssistantTurn =
    { storyText : String
    , image : ImageData
    , suggestedActions : List String
    , newItems : List Item
    , adventureCompleted : Bool
    , debug : Maybe DebugMeta
    }

type alias ImageData =
    { mimeType : String
    , base64 : String
    , prompt : Maybe String
    }
```

Top-level:

```elm
type alias GameState =
    { schemaVersion : Int
    , player : Player
    , currentAdventure : Maybe CurrentAdventure
    , ui : UiState
    }
```

### 3.3 Persistence: localStorage + schema versioning
- On load:
  - JS reads localStorage key (e.g. `hpAdventure:v1`) and passes it as Elm `flags`.
  - Elm decodes flags into `GameState`; if decode fails → use defaults.
- On state changes:
  - Elm sends encoded JSON to JS via port `saveState`.
  - JS writes to localStorage.
- Add `schemaVersion` and a small migration layer in Elm (so future changes don’t break existing saves).

### 3.4 UI layout implementation
**Two-column layout** per assistant response (and scrollable feed):

- Each **turn** renders as a vertical “row block”:
  - Left column:
    - user action (small)
    - assistant story text (clean, German)
    - suggested choice buttons (2–3)
  - Right column:
    - generated image (from base64)

Implementation details:
- Use CSS Grid for each row: `grid-template-columns: 2fr 1fr;`
- Feed container scrolls; auto-scroll to bottom on new assistant response.
- While waiting:
  - Show a “thinking…” placeholder on the left and a skeleton image on the right.

### 3.5 Command handling (preserve CLI semantics, but UI-first)
Before calling backend, Elm should intercept these commands locally:
- `inventar` → open inventory panel/modal
- `geschichte` → open history panel/modal
- `aufgeben` → confirm, then clear `currentAdventure`
- `start`:
  - If no current adventure: create one and also send `action="start"` to backend.
  - If current adventure exists: show message “Du musst dein aktuelles Abenteuer erst beenden!”

### 3.6 API call flow from Elm (`/api/story`)
On “send action”:
1. Append a new `Turn` with `assistant = Nothing`
2. Build request:
   - Player state (name, house, inventory, completed summaries, stats)
   - Current adventure metadata (title, startedAt)
   - Conversation history derived from turns:
     - Flatten `[ userAction, assistant.storyText ]` into `{role, content}` list
   - New user action
3. POST `/api/story`
4. On response:
   - Fill the pending `Turn.assistant`
   - Merge `newItems` into `player.inventory` (dedupe by name, as CLI does)
   - If `adventureTitle` returned and title missing → set it
   - If `adventureCompleted`:
     - Move `{title, summary, completedAt}` into `player.completedAdventures`
     - Increment `player.stats`
     - Clear `currentAdventure` (like CLI)

### 3.7 Offline support (viewing history)
Two layers:
1. **State availability:** because state is in `localStorage`, history renders offline automatically.
2. **App shell availability:** add a small `sw.js` to cache:
   - `index.html`, `elm.js`, `styles.css`, `app.js`
   - This ensures the page can load offline and then render the locally saved history.

If offline:
- Disable “send action” button
- Show a banner: “Offline: Du kannst die Geschichte ansehen, aber keine neuen Züge spielen.”

---

## 4) Data models — JSON contracts between frontend and backend

### 4.1 `POST /api/story` — Request JSON

```json
{
  "player": {
    "name": "Hermine",
    "houseName": "Gryffindor",
    "inventory": [
      { "name": "Zauberstab", "description": "Dein treuer Zauberstab aus Ollivanders Laden", "foundAt": "2026-01-01T10:00:00Z" }
    ],
    "completedAdventures": [
      { "title": "Die Flüsternde Rüstung", "summary": "Sie entdeckte ein Geheimfach...", "completedAt": "2025-12-20T18:12:00Z" }
    ],
    "stats": { "adventuresCompleted": 1, "totalTurns": 14 }
  },
  "currentAdventure": {
    "title": null,
    "startedAt": "2026-01-01T10:05:00Z"
  },
  "conversationHistory": [
    { "role": "user", "content": "start" },
    { "role": "assistant", "content": "Du stehst in der Großen Halle..." }
  ],
  "action": "Ich gehe leise Richtung Tür."
}
```

Notes:
- `conversationHistory` should be **clean story text** (no `[OPTION]`/`[SZENE]` markers), to reduce tokens and prevent meta from polluting the next turn.
- The backend rebuilds the system prompt from `player`.

### 4.2 `POST /api/story` — Response JSON

```json
{
  "assistant": {
    "storyText": "Du schleichst über die kalten Steinplatten ... Was tust du?",
    "suggestedActions": [
      "Den Zauberstab heben und Lumos wirken",
      "An der Tür lauschen",
      "Umkehren und Hilfe holen"
    ],
    "newItems": [
      { "name": "Silberner Schlüssel", "description": "Kalt, schwer, mit einem eingravierten Raben", "foundAt": "2026-01-01T10:07:12Z" }
    ],
    "adventure": {
      "title": "Die Tür im Nordturm",
      "completed": false,
      "summary": null,
      "completedAt": null
    },
    "image": {
      "mimeType": "image/webp",
      "base64": "UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASo...",
      "prompt": "Dunkler Hogwarts-Korridor bei Nacht, Kerzenlicht..."
    }
  }
}
```

### 4.3 Error payload (backend → frontend)
Standardize on:

```json
{
  "error": {
    "code": "UPSTREAM_TIMEOUT",
    "message": "Die KI hat zu lange gebraucht. Bitte versuche es erneut.",
    "requestId": "abc-123"
  }
}
```

---

## 5) Deployment steps for Railway

### Option A (fastest): commit built frontend assets
1. Run `frontend/build.sh` locally:
   - `elm make ... --output=elm.js`
   - Copy `elm.js`, `index.html`, `styles.css`, `app.js`, `sw.js` → `backend/src/main/resources/public`
2. Push to GitHub.
3. Railway:
   - New project → deploy from GitHub repo
   - Detect Java/Gradle
   - Build command: `./gradlew clean build`
   - Start command: `java -jar backend/build/libs/backend-all.jar` (configure via Gradle shadowJar or application plugin)
4. Add environment variables:
   - `ANTHROPIC_API_KEY`
   - `OPENAI_API_KEY`
5. Ensure the app binds to `PORT`.

### Option B (recommended): Railway builds both frontend + backend
Use a **Dockerfile** or Railway’s build config so the build is reproducible:
- Stage 1: install Elm, build frontend → produce static assets
- Stage 2: Gradle build → jar
- Runtime: run jar

This keeps `main` branch always deployable without manual asset copying.

### Verification checklist after deploy
- `GET /health` returns OK
- Opening root URL loads Elm app
- First-time onboarding works
- `start` generates story + image
- Inventory updates when `[NEUER GEGENSTAND]` appears
- Adventure completion generates summary + clears current adventure

---

## 6) Testing strategy

### Backend tests
1. **Unit tests (pure)**
   - `PromptBuilderTest`: inventory + last 5 adventure summaries included
   - `ItemParserTest`: regex correctness; dedupe logic expectations
   - `CompletionParserTest`: marker detection
   - `OptionsParserTest` and `SceneParserTest`: marker parsing and fallback behavior
2. **Integration tests**
   - `StoryRoutesTest` uses Javalin testtools + a `StoryHandler` stub to validate HTTP behavior (200, 400, 429, upstream errors).
   - Keep `StoryServiceTest` for end-to-end Anthropic/OpenAI integration paths.
3. **Contract tests**
   - Snapshot JSON tests: ensure Elm decoders won’t break
4. **Resilience**
   - Simulate upstream 429/500/timeout and assert correct error payload

### Frontend tests
1. **Elm unit tests (elm-test)**
   - Update logic:
     - “start” creates currentAdventure
     - “aufgeben” clears adventure
     - applying `newItems` merges/dedupes
     - completion moves into `completedAdventures`
2. **Decoder tests**
   - Ensure response decoder handles missing optional fields (e.g. no new items)
3. **E2E smoke tests (Playwright)**
   - Mock `/api/story` at the browser layer (no API keys required)
   - Covers onboarding → first story → suggested action → completion → history/stats

---

## 7) Order of implementation (recommended milestones)

### Milestone 0 — Repo + skeleton (1 day)
- Create repo structure
- Backend serves `/health` and static `index.html`
- Elm app loads with a placeholder screen
- localStorage load/save wiring works

### Milestone 1 — End-to-end story (no images yet) (1–2 days)
- Implement `/api/story` with Anthropic story generation
- Elm:
  - Setup screen (name + house)
  - Current adventure screen
  - Send action → receive story text → append to feed

### Milestone 2 — Mechanics parity (1–2 days)
- Backend:
  - Item parsing + completion detection
  - Title generation (first exchange)
  - Summary generation (on completion)
- Elm:
  - Inventory view
  - History view (completed adventures list)

### Milestone 3 — Images for every response (1–2 days)
- Add `[SZENE: ...]` prompt marker + parser
- Implement OpenAI image generation call using `gpt-image-1` via Image API (base64 output) ([platform.openai.com](https://platform.openai.com/docs/api-reference/images/generate))
- Elm renders image alongside each assistant response
- Tune `output_format/output_compression/quality` to keep payload small ([platform.openai.com](https://platform.openai.com/docs/api-reference/images/generate))

### Milestone 4 — Choice buttons (0.5–1 day)
- Add `[OPTION: ...]` markers + parser
- Elm renders buttons; clicking sends action

### Milestone 5 — Offline UX polish (0.5–1 day)
- Add service worker caching for app shell
- Offline banner + disable send
- Ensure localStorage schema versioning

### Milestone 6 — Streaming (optional)
- Backend: stream Anthropic tokens (SSE)
- Frontend: implement streaming via JS fetch + ports (Elm receives partial text)  
  Anthropic streaming event types: `content_block_delta` / `text_delta`. ([docs.anthropic.com](https://docs.anthropic.com/de/docs/build-with-claude/streaming?utm_source=openai))  
- Handle image generation after final text (send image as final event or follow-up request)

---

## Appendix: “Minimal but robust” prompt templates (backend-owned)

### System prompt (based on `adventure.js`, with metadata)
- Keep your current rules verbatim where possible.
- Add output contract at the end:

- Must always include:
  - `Was tust du?`
  - 2–3 `[OPTION: ...]`
  - 1 `[SZENE: ...]`
- Keep:
  - `[NEUER GEGENSTAND: ... | ...]` only for special items
  - `[ABENTEUER ABGESCHLOSSEN]` at natural ending

### Summary prompt (port from CLI)
- Same as `SUMMARY_PROMPT` in `adventure.js`

### Title prompt (port from CLI)
- Same as `generateTitle` prompt in `adventure.js` (max 5 German words)

---

If you want, I can also provide:
- A concrete `StoryRequest`/`StoryResponse` Java DTO set (ready to paste),
- A suggested `PromptBuilder` implementation outline in Java (including exact marker stripping rules),
- Elm decoders/encoders skeleton for the exact JSON contracts above.
