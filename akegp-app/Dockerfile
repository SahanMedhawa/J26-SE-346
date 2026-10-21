FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY akegp-shared-kernel akegp-shared-kernel
COPY akegp-rules akegp-rules
COPY akegp-conformance akegp-conformance
COPY akegp-enforcement akegp-enforcement
COPY akegp-analytics akegp-analytics
COPY akegp-app akegp-app
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl akegp-app -am package -DskipTests

FROM eclipse-temurin:25-jre
COPY --from=build /build/akegp-app/target/akegp-app-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
