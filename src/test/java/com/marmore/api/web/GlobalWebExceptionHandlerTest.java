package com.marmore.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.marmore.api.imageedit.web.ImageEditException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testes do {@link GlobalWebExceptionHandler}: traduz excecoes reativas em JSON com o status certo.
 *
 * <p>Cobertura:
 *
 * <ul>
 *   <li>{@link ImageEditException} -> status embutido + mensagem (criterio de aceite).
 *   <li>Excecao generica -> 500 + mensagem generica (criterio de aceite).
 *   <li>{@link ResponseStatusException} -> status da excecao.
 *   <li>Escapamento de aspas (JSON valido).
 *   <li>Contrato: implementa {@link WebExceptionHandler} e tem order negativa (rodar antes do
 *       {@code ResponseStatusExceptionHandler} padrao).
 * </ul>
 */
class GlobalWebExceptionHandlerTest {

  private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder().build();

  private final GlobalWebExceptionHandler handler = new GlobalWebExceptionHandler(MAPPER);

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.post("/images/edit").build());
  }

  private static String body(MockServerWebExchange exchange) {
    return exchange.getResponse().getBodyAsString().block();
  }

  @DisplayName("ImageEditException devolve o status embutido na excecao")
  @Test
  void imageEditExceptionDevolveStatusEmbutido() {
    ImageEditException ex = new ImageEditException(HttpStatus.SERVICE_UNAVAILABLE, "upstream down");
    MockServerWebExchange exchange = exchange();

    StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isNotNull()
        .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
    assertThat(body(exchange)).isEqualTo("{\"error\":\"upstream down\"}");
  }

  @DisplayName("excecao generica devolve 500 com mensagem generica")
  @Test
  void excecaoGenericaDevolve500() {
    RuntimeException ex = new RuntimeException("boom interno");
    MockServerWebExchange exchange = exchange();

    StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(body(exchange)).isEqualTo("{\"error\":\"erro interno\"}");
  }

  @DisplayName("ResponseStatusException devolve o status da excecao")
  @Test
  void responseStatusExceptionDevolveStatusDaExcecao() {
    ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "conflito");
    MockServerWebExchange exchange = exchange();

    StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(exchange)).isEqualTo("{\"error\":\"conflito\"}");
  }

  @DisplayName("mensagem com aspas e escapada e produz JSON valido")
  @Test
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  void mensagemComAspasEscapadaValida() {
    ImageEditException ex = new ImageEditException(HttpStatus.BAD_REQUEST, "campo \"x\" invalido");
    MockServerWebExchange exchange = exchange();

    StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

    String json = body(exchange);
    assertThat(json).isEqualTo("{\"error\":\"campo \\\"x\\\" invalido\"}");
    assertThat(MAPPER.readTree(json).get("error").asText()).isEqualTo("campo \"x\" invalido");
  }

  @DisplayName("handler implementa WebExceptionHandler")
  @Test
  void implementaWebExceptionHandler() {
    assertThat(handler).isInstanceOf(WebExceptionHandler.class);
  }

  @DisplayName("ordem negativa roda antes do ResponseStatusExceptionHandler padrao")
  @Test
  void ordemNegativaRodaAntesDoResponseStatusExceptionHandler() {
    assertThat(handler).isInstanceOf(Ordered.class);
    assertThat(handler.getOrder()).isLessThan(0);
  }
}
