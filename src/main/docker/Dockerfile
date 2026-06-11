FROM maven:3.9.6-openjdk-21-slim AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

#
# Package stage
#
FROM openjdk:21-alpine
EXPOSE 80
COPY --from=build /home/app/target/meta-whatsapp-webhook-1.0.0-SNAPSHOT.jar app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
