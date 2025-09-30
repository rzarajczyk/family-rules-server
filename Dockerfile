FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY ./ .
RUN ./gradlew clean bootJar -x test --stacktrace --no-daemon

FROM eclipse-temurin:17-jdk
EXPOSE 8080
WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar
RUN apt-get install -y tzdata

ENTRYPOINT java \
  -XX:InitialRAMPercentage=75.0 \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
#  -Xlog:gc*:stderr:time \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar app.jar