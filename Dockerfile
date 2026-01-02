# Stage 1: Build Elm frontend
# Use x86_64 platform because Elm doesn't have ARM64 binaries
FROM --platform=linux/amd64 node:22-alpine AS frontend-builder

RUN npm install -g elm

WORKDIR /app/frontend
COPY frontend/elm.json ./
COPY frontend/src ./src

RUN elm make src/Main.elm --optimize --output=elm.js

# Stage 2: Build Java backend
FROM eclipse-temurin:21-jdk-alpine AS backend-builder

WORKDIR /app/backend

# Copy Gradle wrapper and build files
COPY backend/gradlew ./
COPY backend/gradle ./gradle
COPY backend/build.gradle.kts backend/settings.gradle.kts ./

# Copy source code
COPY backend/src ./src

# Copy frontend assets into backend resources
COPY --from=frontend-builder /app/frontend/elm.js ./src/main/resources/public/elm.js
COPY frontend/public/index.html frontend/public/styles.css frontend/public/app.js frontend/public/sw.js ./src/main/resources/public/

# Build fat JAR
RUN chmod +x gradlew && ./gradlew shadowJar --no-daemon

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=backend-builder /app/backend/build/libs/hp-adventure.jar ./hp-adventure.jar

# Railway provides PORT env var
ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "hp-adventure.jar"]
