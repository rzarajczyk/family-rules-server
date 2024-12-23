FROM eclipse-temurin:21-jdk
EXPOSE 8080
WORKDIR /app
COPY build/libs/app.jar app.jar
RUN apt-get install -y tzdata
ENTRYPOINT java ${JAVA_OPTS} -jar app.jar
