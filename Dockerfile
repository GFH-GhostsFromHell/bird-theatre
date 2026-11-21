FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY portal/ /source/
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/target/bird-theatre-*.jar /app/app.jar
ENV SERVER_PORT=10000
EXPOSE 10000
USER 10001
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=70 -jar /app/app.jar"]
