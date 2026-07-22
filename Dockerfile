# Multi-stage build: compiles the DhrLang fat JAR inside the image so `docker build .`
# always packages the current source tree instead of a stale, hardcoded JAR filename
# (previously: build/libs/DhrLang-2.0.0.jar, which stopped existing after the 2.0.0 tag).
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /src

COPY . .

RUN set -eu; \
    sed -i 's/\r$//' ./gradlew; \
    chmod +x ./gradlew; \
    ./gradlew --no-daemon clean shadowJar; \
    RUNTIME_JARS="$(find build/libs -maxdepth 1 -type f -name 'DhrLang-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar')"; \
    JAR_COUNT="$(printf '%s\n' "$RUNTIME_JARS" | sed '/^$/d' | wc -l)"; \
    if [ "$JAR_COUNT" -ne 1 ]; then echo "Expected exactly one runnable DhrLang JAR, found $JAR_COUNT" >&2; exit 1; fi; \
    cp "$RUNTIME_JARS" /tmp/dhrlang.jar

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DhrLang Team"
LABEL description="DhrLang compiler and runtime"

WORKDIR /app

COPY --from=build /tmp/dhrlang.jar /app/dhrlang.jar

ENTRYPOINT ["java", "-jar", "/app/dhrlang.jar"]
