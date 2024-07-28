FROM eclipse-temurin:21-jdk
EXPOSE 8080
WORKDIR /app
COPY build/libs/app.jar app.jar
ENTRYPOINT java ${JAVA_OPTS} -jar app.jar
