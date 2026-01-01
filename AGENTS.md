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

Developer notes:
- `POST /api/story` now calls Anthropic `/v1/messages`; requires `ANTHROPIC_API_KEY` (optional: `ANTHROPIC_MODEL`, `ANTHROPIC_BASE_URL`).
- Image generation uses OpenAI `/v1/images/generations`; requires `OPENAI_API_KEY` (optional: `OPENAI_BASE_URL`, `OPENAI_IMAGE_MODEL`, `OPENAI_IMAGE_FORMAT`, `OPENAI_IMAGE_COMPRESSION`, `OPENAI_IMAGE_QUALITY`, `OPENAI_IMAGE_SIZE`).
- Prompt + parsing live in `backend/src/main/java/com/example/hpadventure/services` and `backend/src/main/java/com/example/hpadventure/parsing`.
- Backend tests: `cd backend && ./gradlew test`.
- Elm HTTP support requires `elm/http`; add deps with `elm install` (don’t hand-edit versions).
- After frontend changes, run `./frontend/build.sh` to refresh `backend/src/main/resources/public`.
- The Elm app expects `/api/story` to accept `{ player, currentAdventure, conversationHistory, action }` and return `assistant.storyText`, `assistant.suggestedActions`, plus `assistant.image` (base64).
- UI command shortcuts (handled client-side): `inventar`, `geschichte`, `aufgeben`, `start`.
