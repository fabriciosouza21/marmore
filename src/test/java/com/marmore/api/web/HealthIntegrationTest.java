package com.marmore.api.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Teste de integracao do {@code GET /health} na cadeia de seguranca: o endpoint e PUBLICO (sem
 * header {@code X-API-Key}) para servir de liveness/healthcheck do container e consulta de versao
 * em producao. Sobe o contexto WebFlux completo ({@code RANDOM_PORT}).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthIntegrationTest {

  private WebTestClient client;

  @LocalServerPort private int porta;

  @BeforeEach
  void setUp() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
  }

  @DisplayName("GET /health sem X-API-Key: 200 com status ok e versao nao vazia")
  @Test
  void healthPublicoSemApiKey() {
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
        .isNotEmpty();
  }
}
