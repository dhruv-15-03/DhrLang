# Multi-stage build: compiles the DhrLang fat JAR inside the image so `docker build .`
# always packages the current source tree instead of a stale, hardcoded JAR filename
# (previously: build/libs/DhrLang-2.0.0.jar, which stopped existing after the 2.0.0 tag).
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /src

COPY . .

RUN chmod +x ./gradlew && ./gradlew --no-daemon clean shadowJar

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DhrLang Team"
LABEL description="DhrLang compiler and runtime"

WORKDIR /app

COPY --from=build /src/build/libs/*.jar /app/dhrlang.jar

ENTRYPOINT ["java", "-jar", "/app/dhrlang.jar"]
