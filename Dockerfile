# Imagem da API do marmore: build Maven -> JRE 26.
# Contexto de build = raiz do projeto (onde fica este Dockerfile).
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app
RUN addgroup -S marmore && adduser -S marmore -G marmore
# stone-path resolve em ${user.dir}/data/granito.png; user.dir e o WORKDIR.
COPY --from=build --chown=marmore:marmore /build/target/api-*.jar app.jar
COPY --chown=marmore:marmore data ./data
USER marmore
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
