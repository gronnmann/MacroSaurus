FROM maven:3.9.16-eclipse-temurin-26-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -pl backend dependency:go-offline -DskipTests

COPY backend/src backend/src
RUN mvn -B -pl backend package -DskipTests

FROM eclipse-temurin:26-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S macrosaurus \
    && adduser -S -G macrosaurus macrosaurus

WORKDIR /app
COPY --from=build /workspace/backend/target/backend-*.jar app.jar

USER macrosaurus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
