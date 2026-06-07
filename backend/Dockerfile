# ── Build stage ──────────────────────────────────────────────────────────────
# Build context : racine du projet (k:/Stage) pour accéder à schemas/avro/
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY backend/pom.xml .
# Télécharger les dépendances en cache (layer séparé)
RUN mvn dependency:go-offline -q
COPY backend/src ./src
# Schémas Avro — avro-maven-plugin lit depuis /build/../schemas/avro = /schemas/avro
COPY schemas/avro /schemas/avro
RUN mvn package -Dmaven.test.skip=true -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S imf && adduser -S imf -G imf
COPY --from=builder /build/target/imf-pipeline-backend-*.jar app.jar
USER imf
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
