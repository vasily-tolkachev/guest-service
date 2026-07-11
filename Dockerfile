FROM eclipse-temurin:25-jre
WORKDIR /app

COPY target/*SNAPSHOT.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
