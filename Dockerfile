FROM maven:3.10.0-eclipse-temurin-25-noble@sha256:51a751cf747bad5968979a66142d443e8d94854172c3891ae2496edec544c6b3 AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-noble@sha256:2ef7a81085e0502d7d4ff7e7c0257b6d4f46433f8ea6021180aa3c8a2b9ca3ec
ARG SOURCE_SHA
ARG SOURCE_TREE
ARG SOURCE_DIRTY
LABEL org.opencontainers.image.title="Synthetic Stock Reservation Service" \
      org.opencontainers.image.revision="${SOURCE_SHA}" \
      io.project20.source.tree="${SOURCE_TREE}" \
      io.project20.source.dirty="${SOURCE_DIRTY}"
WORKDIR /app
COPY --from=build /build/target/stock-reservation-service-0.1.0-SNAPSHOT.jar /app/service.jar
USER 10001:10001
EXPOSE 8220
ENTRYPOINT ["java","-jar","/app/service.jar"]
