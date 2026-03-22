FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DhrLang Team"
LABEL description="DhrLang compiler and runtime"

WORKDIR /app

COPY build/libs/DhrLang-2.0.0.jar /app/dhrlang.jar

ENTRYPOINT ["java", "-jar", "/app/dhrlang.jar"]
