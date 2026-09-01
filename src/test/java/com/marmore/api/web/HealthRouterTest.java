package com.marmore.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Testes do {@code GET /health} (liveness + versao do build). Contrato do JSON de saida: {@code
 * status} sempre "ok" e {@code version} exatamente a versao do {@link BuildProperties} (gerada pelo
 * goal build-info do spring-boot-maven-plugin a partir do {@code <version>} do pom). WebTestClient
 * ligado direto ao {@link HealthRouter}, sem contexto Spring.
 */
class HealthRouterTest {

  private WebTestClient client;

  @BeforeEach
  void setUp() {
    // Em runtime a autoconfiguracao do Boot carrega o build-info.properties e entrega as chaves ja
    // sem o prefixo "build."; no stub manual usa-se a chave pura ("version").
    Properties entries = new Properties();
    entries.setProperty("version", "9.9.9-teste");
    client =
        WebTestClient.bindToRouterFunction(
                new HealthRouter().healthRoute(new HealthHandler(new BuildProperties(entries))))
            .build();
  }

  @DisplayName("GET /health: 200 com status ok e a versao do build")
  @Test
  void healthRetornaStatusOkVersaoDoBuild() {
    client
        .get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("ok")
        .jsonPath("$.version")
        .isEqualTo("9.9.9-teste");
  }

  @DisplayName("GET /health: corpo tem exatamente as chaves status e version")
  @Test
  void healthSoExpoeDuasChaves() {
    String corpo =
        client
            .get()
            .uri("/health")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertThat(corpo).containsOnlyOnce("status").containsOnlyOnce("version").doesNotContain("key");
  }
}
