FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system kongklang && useradd --system --gid kongklang kongklang
COPY --from=build /build/target/kongklang-0.0.1-SNAPSHOT.jar /app/app.jar
USER kongklang
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
