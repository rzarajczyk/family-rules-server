FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY ./ .
RUN ./gradlew clean bootJar -x test --stacktrace --no-daemon

FROM eclipse-temurin:21-jdk
EXPOSE 8080
WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar
RUN apt-get install -y tzdata

ENTRYPOINT java \
  -XX:InitialRAMPercentage=75.0 \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 \
  -Djava.security.egd=file:/dev/./urandom \
  -XX:+TieredCompilation \
  -XX:TieredStopAtLevel=1 \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Dspring.jmx.enabled=false \
  -Dspring.main.lazy-initialization=true \
  -jar app.jar