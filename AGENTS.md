Make sure we've feedback loops.
Strife towards creating them to verify what you build.
Ask Lars for clarification when you're not sure about something or encounter problems.
Update AGENTS.md with instructions for developers of this project, things they need to know. Make sure to update the document regularly.
Commit often, we want those checkpoints as safety net.

Project notes:
- Backend lives in `backend/` (Java 21, Javalin). Run with `./gradlew run` from `backend/`.
- Frontend lives in `frontend/` (Elm 0.19). Build with `./frontend/build.sh` (copies assets into `backend/src/main/resources/public`).
- Static assets served from `backend/src/main/resources/public`.
- localStorage key is `hpAdventure:v1` (see `frontend/public/app.js`).
- Frontend schema version is `2` (see `frontend/src/Model.elm` + `frontend/src/Codec.elm`).
- Service worker lives in `frontend/public/sw.js` and is registered from `frontend/public/app.js`.
- Online/offline status is sent from `frontend/public/app.js` to Elm via the `onlineStatus` port; Elm stores it in `GameState.isOnline` and uses it to show the offline banner + disable sends (not persisted to localStorage).
- Story feed auto-scroll uses the `story-feed` element id in `frontend/src/View.elm` (keep it stable if you refactor the layout).

Developer notes:
- Backend logs via slf4j-simple to stdout; `/api/story` + `/api/story/stream` emit `requestId` in logs and `X-Request-Id` response header for correlation.
- Anthropic Messages API expects `system` as a list of content blocks; client wraps the system prompt in `{ type: "text", text: ... }`.
- Anthropic request payloads (system + messages) are logged in `AnthropicClient` for debugging.
- Prompt now explicitly requires 2-3 `[OPTION: ...]` lines after "Was tust du?" to keep UI options populated.
- Image prompts now instruct "no characters/portraits"; scenes only (locations, objects, enemies/creatures/animals).
- `POST /api/story` now calls Anthropic `/v1/messages`; requires `ANTHROPIC_API_KEY` (optional: `ANTHROPIC_MODEL`, `ANTHROPIC_BASE_URL`).
- Image generation uses OpenAI `/v1/images/generations`; requires `OPENAI_API_KEY` (optional: `OPENAI_BASE_URL`, `OPENAI_IMAGE_MODEL`, `OPENAI_IMAGE_FORMAT`, `OPENAI_IMAGE_COMPRESSION`, `OPENAI_IMAGE_QUALITY`, `OPENAI_IMAGE_SIZE`).
- `POST /api/story` is rate-limited in-memory; configure with `RATE_LIMIT_PER_MINUTE` (set to `0` to disable).
- Streaming endpoint: `POST /api/story/stream` (SSE). Client uses JS fetch streaming via `startStoryStream`/`storyStream` ports and falls back to `POST /api/story`.
- Prompt + parsing live in `backend/src/main/java/com/example/hpadventure/services` and `backend/src/main/java/com/example/hpadventure/parsing`.
- HTTP routes live in `backend/src/main/java/com/example/hpadventure/api/HealthRoutes.java` and `backend/src/main/java/com/example/hpadventure/api/StoryRoutes.java` (wired from `App.java`).
- `StoryService` implements `StoryHandler` so routes can be tested with stubs.
- Backend tests: `cd backend && ./gradlew test`.
- `StoryServiceTest` stubs Anthropic/OpenAI via OkHttp MockWebServer for end-to-end service coverage.
- `StoryRoutesTest` uses Javalin testtools to cover API validation, rate limiting, and upstream error mapping.
- E2E smoke tests (Playwright): `npm run test:e2e` (first time: `npx playwright install` to fetch browsers; serves `backend/src/main/resources/public` via `python3 -m http.server`).
- Playwright uses `playwright.config.js` (base URL `http://localhost:4173`, serves `backend/src/main/resources/public`); E2E selectors target `data-testid` attributes in `frontend/src/View.elm`.
- If you touch Elm views, rerun `./frontend/build.sh` so the compiled `elm.js` in `frontend/public` and `backend/src/main/resources/public` stays in sync.
- Elm HTTP support requires `elm/http`; add deps with `elm install` (don’t hand-edit versions).
- After frontend changes, run `./frontend/build.sh` to refresh `backend/src/main/resources/public`.
- The Elm app expects `/api/story` to accept `{ player, currentAdventure, conversationHistory, action }` and return `assistant.storyText`, `assistant.suggestedActions`, plus `assistant.image` (base64).
- UI command shortcuts (handled client-side): `inventar`, `geschichte`, `aufgeben`, `start`.
- Client-side error handling drops the last pending turn on failed story requests to avoid stuck "Die Geschichte schreibt sich..." placeholders (see `frontend/src/Update.elm`).
