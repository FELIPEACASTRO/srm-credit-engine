# Build em duas etapas: o wrapper resolve o Maven; o runtime e JRE puro, nao-root.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
# checkstyle roda no validate (M5): a config precisa existir dentro do build
COPY config config
RUN ./mvnw --batch-mode dependency:go-offline -q
COPY src src
RUN ./mvnw --batch-mode -DskipTests package -q

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /workspace/target/credit-engine-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
