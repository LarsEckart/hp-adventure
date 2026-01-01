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
