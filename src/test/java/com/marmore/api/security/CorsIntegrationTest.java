package com.marmore.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Testes de integracao do CORS da API. O frontend de producao (https://marmoraria.fsmdevs.com,
 * CloudFront) chama a API em https://api.marmoraria.fsmdevs.com: origens distintas exigem que o
 * preflight (OPTIONS com Origin e Access-Control-Request-*) seja respondido pelo filtro de CORS
 * ANTES da autenticacao por API key. Cenario real de navegador: POST multipart com header
 * X-API-Key. Sobe o contexto WebFlux completo ({@code RANDOM_PORT}); os handlers de negocio nao sao
 * alcancados.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CorsIntegrationTest {

  /** Origem do frontend de producao, a unica liberada no CORS. */
  private static final String ORIGEM_FRONTEND = "https://marmoraria.fsmdevs.com";

  private WebTestClient client;

  @LocalServerPort private int porta;

  @BeforeEach
  void setUp() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + porta).build();
  }

  @DisplayName("Preflight do frontend (POST + X-API-Key): 200 com Allow-Origin da propria origem")
  @Test
  void preflightDaOrigemDoFrontendEhPermitido() {
    client
        .options()
        .uri("/images/edit")
        .header(HttpHeaders.ORIGIN, ORIGEM_FRONTEND)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-api-key, content-type")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_FRONTEND);
  }

  @DisplayName("Request real de origem permitida carrega Allow-Origin mesmo sem API key (401)")
  @Test
  void requestRealDaOrigemPermitidaRecebeHeaderCors() {
    client
        .post()
        .uri("/images/edit")
        .header(HttpHeaders.ORIGIN, ORIGEM_FRONTEND)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_FRONTEND);
  }

  @DisplayName("Preflight de origem estranha e rejeitado (403, sem Allow-Origin)")
  @Test
  void preflightDeOrigemEstranhaEhRejeitado() {
    client
        .options()
        .uri("/images/edit")
        .header(HttpHeaders.ORIGIN, "https://evil.example")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }
}
