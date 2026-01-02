Make sure we've feedback loops.
Strife towards creating them to verify what you build.
Ask Lars for clarification when you're not sure about something or encounter problems.
Update AGENTS.md with instructions for developers of this project, things they need to know. Make sure to update the document regularly.
Commit often, we want those checkpoints as safety net.

Project notes:
- Backend lives in `backend/` (Java 21, Javalin). Run with `./gradlew run` from `backend/`.
- Frontend lives in `frontend/` (Elm 0.19). Build with `./frontend/build.sh` (copies assets into `backend/src/main/resources/public`).
- Static assets served from `backend/src/main/resources/public`.
- localStorage key is `hpAdventure:v1` (see `frontend/public/app.js`); password stored separately in `hpAdventure:password`.
- Frontend schema version is `2` (see `frontend/src/Model.elm` + `frontend/src/Codec.elm`).
- Service worker lives in `frontend/public/sw.js` and is registered from `frontend/public/app.js`.
- Online/offline status is sent from `frontend/public/app.js` to Elm via the `onlineStatus` port; Elm stores it in `GameState.isOnline` and uses it to show the offline banner + disable sends (not persisted to localStorage).
- Story feed auto-scroll uses the `story-feed` element id in `frontend/src/View.elm` (keep it stable if you refactor the layout).

Developer notes:
- Backend logs via slf4j-simple to stdout; `/api/story` + `/api/story/stream` emit `requestId` in logs and `X-Request-Id` response header for correlation.
- Anthropic Messages API expects `system` as a list of content blocks; `AnthropicTextProvider` wraps the system prompt in `{ type: "text", text: ... }`.
- Story system prompts plus title/summary prompts are logged where they are built (`PromptBuilder`, `TitleService`, `SummaryService`) to keep logs lean.
- Prompt now explicitly requires 2-3 `[OPTION: ...]` lines after "Was tust du?" to keep UI options populated.
- Image prompts now instruct "no characters/portraits"; scenes only (locations, objects, enemies/creatures/animals).
- New adventures auto-add a starter item ("Zauberstab") to the player inventory; prompt always includes an inventory section.
- Story arc tracking: steps 1-5 intro, 6-13 main arc, 14-15 finale. Server derives step from completed assistant turns and injects it into the prompt.
- UI includes a reset button that clears the `hpAdventure:v1` localStorage key and resets state to defaults.
- `POST /api/story` now calls Anthropic `/v1/messages`; requires `ANTHROPIC_API_KEY` (optional: `ANTHROPIC_MODEL`, `ANTHROPIC_BASE_URL`).
- Provider abstractions live in `backend/src/main/java/com/example/hpadventure/providers`:
  - `TextProvider` interface for text generation (implemented by `AnthropicTextProvider`)
  - `ImageProvider` interface for image generation (implemented by `OpenAiImageProvider`, `OpenRouterImageProvider`)
  - `SpeechProvider` interface for text-to-speech (implemented by `ElevenLabsSpeechProvider`)
- Image generation supports two providers via the `ImageProvider` interface: OpenAI (primary) and OpenRouter (fallback).
  - **OpenAI**: Uses `/v1/images/generations`; requires `OPENAI_API_KEY` (optional: `OPENAI_BASE_URL`, `OPENAI_IMAGE_MODEL`, `OPENAI_IMAGE_FORMAT`, `OPENAI_IMAGE_COMPRESSION`, `OPENAI_IMAGE_QUALITY`, `OPENAI_IMAGE_SIZE`).
  - **OpenRouter**: Uses `/v1/chat/completions` with image-generating models like `google/gemini-2.5-flash-image`; requires `OPENROUTER_API_KEY` (optional: `OPENROUTER_BASE_URL`, `OPENROUTER_IMAGE_MODEL`).
  - Priority: `OPENAI_API_KEY` > `OPENROUTER_API_KEY`. If neither is set, images are disabled.
- `POST /api/story` is rate-limited in-memory; configure with `RATE_LIMIT_PER_MINUTE` (set to `0` to disable).
- Authentication: Set `APP_PASSWORDS` env var with format `name:password,name2:password2,...` to enable password protection. Protected routes: `/api/story`, `/api/story/stream`, `/api/tts`. Validation endpoint: `POST /api/auth/validate`. Frontend shows password screen until validated; password stored in `hpAdventure:password` localStorage and sent via `X-App-Password` header. If `APP_PASSWORDS` is not set, authentication is disabled.
- Streaming endpoint: `POST /api/story/stream` (SSE). Client uses JS fetch streaming via `startStoryStream`/`storyStream` ports and falls back to `POST /api/story`. Streaming sends `delta` + `final_text` (story content) first, then an `image` event when image generation completes (or `image_error` on failure).
- TTS endpoint: `POST /api/tts` returns streamed `audio/mpeg` for a story turn. Requires `ELEVENLABS_API_KEY`; voice id defaults to `g1jpii0iyvtRs8fqXsd1` (override with `ELEVENLABS_VOICE_ID`, optional: `ELEVENLABS_MODEL`, `ELEVENLABS_BASE_URL`, `ELEVENLABS_OUTPUT_FORMAT`, `ELEVENLABS_OPTIMIZE_STREAMING_LATENCY`). Frontend auto-plays per assistant turn via the `speakStory` port.
- Streaming deltas are filtered server-side to strip `[OPTION: ...]`/`[SZENE: ...]`/inventory markers; UI options arrive with the `final_text` event (or the non-streaming JSON response).
- Story text is sanitized server-side to strip simple Markdown markers (`*`, `_`, `` ` ``) in both streaming deltas and final responses.
- Title output from Anthropic is sanitized server-side (strip markdown headers, clamp to 5 words, trim trailing stopwords) before being stored/displayed.
- Streaming deltas may include whitespace-only chunks; do not drop/trim them or words may concatenate.
- Each assistant turn is expected to include an image; if a turn shows text without an image, check that the SSE `image` event arrived (otherwise the UI keeps `image = Nothing`) and correlate backend OpenAI image logs with the `X-Request-Id`.
- The UI only shows image placeholders while the latest turn is loading; if image generation fails and an error appears, the image column is hidden for that turn.
- The service worker caches `elm.js`/`styles.css`; bump `CACHE_NAME` in `frontend/public/sw.js` (and copy to backend public) whenever you change frontend assets (Elm output, CSS, app.js, index.html) or the UI doesn’t reflect recent changes in production.
- Prompt + parsing live in `backend/src/main/java/com/example/hpadventure/services` and `backend/src/main/java/com/example/hpadventure/parsing`.
- HTTP routes live in `backend/src/main/java/com/example/hpadventure/api/HealthRoutes.java` and `backend/src/main/java/com/example/hpadventure/api/StoryRoutes.java` (wired from `App.java`).
- `StoryService` implements `StoryHandler` so routes can be tested with stubs.
- Backend tests: `cd backend && ./gradlew test`.
- `StoryServiceTest` stubs Anthropic/OpenAI via OkHttp MockWebServer for end-to-end service coverage.
- `StoryStreamServiceTest` uses in-memory `FakeTextProvider`/`FakeImageProvider` in `backend/src/test/java/com/example/hpadventure/services` to drive stream + image paths without HTTP.
- `StoryRoutesTest` uses Javalin testtools to cover API validation, rate limiting, and upstream error mapping.
- `AnthropicTextProviderSmokeTest` hits the real https://api.anthropic.com endpoint; set `ANTHROPIC_API_KEY` (optional: `ANTHROPIC_MODEL`) to run and expect real API usage/costs.
- E2E smoke tests (Playwright): `npm run test:e2e` (first time: `npx playwright install` to fetch browsers; serves `backend/src/main/resources/public` via `python3 -m http.server`).
- Playwright uses `playwright.config.js` (base URL `http://localhost:4173`, serves `backend/src/main/resources/public`); E2E selectors target `data-testid` attributes in `frontend/src/View.elm`.
- If you touch Elm views, rerun `./frontend/build.sh` so the compiled `elm.js` in `frontend/public` and `backend/src/main/resources/public` stays in sync.
- Elm HTTP support requires `elm/http`; add deps with `elm install` (don’t hand-edit versions).
- After frontend changes, run `./frontend/build.sh` to refresh `backend/src/main/resources/public`.
- The Elm app expects `/api/story` to accept `{ player, currentAdventure, conversationHistory, action }` and return `assistant.storyText`, `assistant.suggestedActions`, plus `assistant.image` (base64).
- UI command shortcuts (handled client-side): `inventar`, `geschichte`, `aufgeben`, `start`.
- Client-side error handling drops the last pending turn on failed story requests to avoid stuck "Die Geschichte schreibt sich..." placeholders (see `frontend/src/Update.elm`).
- Completed adventures remain visible in the story view; inputs are disabled and a "Neues Abenteuer" button returns to the setup screen.

Deployment (Railway):
- App is deployed on Railway (https://railway.app).
- Install Railway CLI: `brew install railway` (macOS) or `npm i -g @railway/cli`.
- Login: `railway login`.
- Link project: `railway link` (select existing project or create new).
- Deploy: `railway up` (pushes current directory, builds via Dockerfile).
- Logs: `railway logs` (stream live logs).
- Env vars: `railway variables` (list), `railway variables set KEY=value` (set).
- Open app: `railway open` (opens deployed URL in browser).
- Build uses multi-stage Dockerfile: (1) builds Elm frontend with Node, (2) builds Java backend with Gradle shadowJar, (3) runs JRE-only image with the fat jar.
- Railway provides `PORT` env var automatically (defaults to 8080 in Dockerfile).
