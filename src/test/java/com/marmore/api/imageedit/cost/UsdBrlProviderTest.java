package com.marmore.api.imageedit.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Testes de {@link UsdBrlProvider}: parseia {@code bid} da AwesomeAPI, usa fallback em erro
 * (HTTP/timeout), e respeita o TTL do cache em memoria (nao refaz antes de expirar, refaz depois).
 *
 * <p>Cada teste cria um {@link UsdBrlProvider} com TTL curto para que a expiracao seja observavel
 * sem dormir demais. O {@link MockWebServer} simula a AwesomeAPI.
 */
class UsdBrlProviderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MockWebServer server;
  private UsdBrlProvider provider;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    String url = server.url("/").toString();
    WebClient.Builder builder = WebClient.builder();
    provider =
        new UsdBrlProvider(
            builder, new UsdBrlProperties(url, Duration.ofSeconds(2), new BigDecimal("5.1075")));
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @DisplayName("parseia o bid da resposta JSON da AwesomeAPI")
  @Test
  void parseiaBidDaRespostaJson() {
    server.enqueue(jsonResponse("6.4321"));

    BigDecimal rate = provider.currentRate().block();

    assertThat(rate).isEqualByComparingTo(new BigDecimal("6.4321"));
  }

  @DisplayName("usa fallback em erro HTTP 500")
  @Test
  void usaFallbackEmErroHttp500() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    BigDecimal rate = provider.currentRate().block();

    assertThat(rate).isEqualByComparingTo(new BigDecimal("5.1075"));
  }

  @DisplayName("nao refaz a chamada antes do TTL expirar (retorna valor em cache)")
  @Test
  void naoRefazAntesDoTtlExpirar() {
    server.enqueue(jsonResponse("6.4321"));

    BigDecimal first = provider.currentRate().block();
    BigDecimal second = provider.currentRate().block();

    assertThat(first).isEqualByComparingTo(new BigDecimal("6.4321"));
    assertThat(second).isEqualByComparingTo(new BigDecimal("6.4321"));
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @DisplayName("refaz a chamada apos o TTL expirar")
  @Test
  void refazAposTtlExpirar() throws InterruptedException {
    server.enqueue(jsonResponse("6.4321"));
    server.enqueue(jsonResponse("6.5000"));

    BigDecimal first = provider.currentRate().block();
    Thread.sleep(2_500L);
    BigDecimal second = provider.currentRate().block();

    assertThat(first).isEqualByComparingTo(new BigDecimal("6.4321"));
    assertThat(second).isEqualByComparingTo(new BigDecimal("6.5000"));
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @DisplayName("usa fallback em timeout (resposta demora mais que o timeout de 5s do provider)")
  @Test
  void usaFallbackEmTimeout() {
    // MockWebServer atrasa a resposta em 6s; o timeout de 5s do provider dispara o fallback.
    server.enqueue(jsonResponse("6.4321").setHeadersDelay(6, TimeUnit.SECONDS));

    BigDecimal rate = provider.currentRate().block();

    assertThat(rate).isEqualByComparingTo(new BigDecimal("5.1075"));
  }

  private MockResponse jsonResponse(String bid) {
    String body;
    try {
      body = MAPPER.writeValueAsString(Map.of("USDBRL", Map.of("bid", bid)));
    } catch (tools.jackson.core.JacksonException e) {
      throw new RuntimeException(e);
    }
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }
}
