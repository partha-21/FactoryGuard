# Stage 1: build the Java program (you do not need Java or Maven installed)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -Dmaven.test.skip=true

# Stage 2: a small image that only runs the program
FROM eclipse-temurin:21-jre
WORKDIR /app
# Keep memory small so everything fits on a modest server
ENV JAVA_TOOL_OPTIONS=-Xmx192m
# The "release" identity is baked into the image. v1.1 is built with BUG_MODE=true.
ARG APP_VERSION=v1.0
ARG BUG_MODE=false
ENV APP_VERSION=${APP_VERSION}
ENV BUG_MODE=${BUG_MODE}
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
