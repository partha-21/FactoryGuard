FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
# Keep memory small so everything fits on a modest server
ENV JAVA_TOOL_OPTIONS=-Xmx256m
# The Docker command-line tool: FactoryGuard uses it to start/stop the demo app versions.
COPY --from=docker:27-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=build /build/target/guard.jar guard.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "guard.jar"]
