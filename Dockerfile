# Multi-stage Dockerfile for finsecure-validator
# First stage builds the jar with (Maven + full JDK + your source code + downloaded libraries)
# Second stage is the final runtime image with only the jar package and JRE. 

# 1: build the jar 
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies. Docker caches this layer,
# so later builds skip the download unless pom.xml changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy from src on pc to /app/src in image
COPY src ./src   
# Skip Tests here because Jenkins runs them in its own Test stage
RUN mvn -B package -DskipTests

#  2: build small runtime image 
FROM eclipse-temurin:21-jre
WORKDIR /app

# Security: run as a non-root user
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]