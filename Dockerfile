FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
COPY backend ./backend
COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist
RUN mvn -pl backend -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/relata-backend-0.1.0-SNAPSHOT.jar ./relata.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/relata.jar"]
