FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
# Keep memory small so everything fits on a modest server
ENV JAVA_TOOL_OPTIONS=-Xmx128m
COPY --from=build /build/target/simulator.jar simulator.jar
ENTRYPOINT ["java", "-jar", "simulator.jar"]
