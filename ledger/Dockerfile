FROM node:24-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY web/ ./
RUN npm run build -- --outDir dist --emptyOutDir

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY ledger/gradlew ledger/settings.gradle ledger/build.gradle ./
COPY ledger/gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY ledger/src src
COPY --from=web /web/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
