package com.marmore.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Testes do {@link ApiKeyAuthWebFilter} (filtro reativo). */
class ApiKeyAuthWebFilterTest {

  private static ApiKeyProperties propsComChave(String chave) {
    ApiKeyProperties props = new ApiKeyProperties();
    props.setKey(chave);
    return props;
  }

  private static MockServerWebExchange exchange(String headerValue) {
    MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("/images/edit");
    if (headerValue != null) {
      builder.header(ApiKeyAuthWebFilter.HEADER, headerValue);
    }
    return MockServerWebExchange.from(builder);
  }

  private static DefaultWebFilterChain chainQueMarca(boolean[] flag) {
    return new DefaultWebFilterChain(
        (exchange) -> {
          flag[0] = true;
          return Mono.empty();
        },
        java.util.List.of());
  }

  @DisplayName("chave correta autentica e continua a chain")
  @Test
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  void chaveCorretaAutenticaEContinuaChain() {
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(propsComChave("segredo-certo"));
    MockServerWebExchange exchange = exchange("segredo-certo");
    boolean[] flag = new boolean[] {false};

    StepVerifier.create(filter.filter(exchange, chainQueMarca(flag))).verifyComplete();

    assertThat(flag[0]).isTrue();
  }

  @DisplayName("chave errada rejeita com 401 JSON e nao continua a chain")
  @Test
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  void chaveErradaRejeitaCom401ESemContinuar() {
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(propsComChave("segredo-certo"));
    MockServerWebExchange exchange = exchange("errada");
    boolean[] flag = new boolean[] {false};

    StepVerifier.create(filter.filter(exchange, chainQueMarca(flag))).verifyComplete();

    assertThat(flag[0]).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isNotNull()
        .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
    String corpo = corpo(exchange);
    assertThat(corpo).contains("API key ausente ou invalida");
  }

  @DisplayName("header ausente rejeita com 401")
  @Test
  void headerAusenteRejeitaCom401() {
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(propsComChave("segredo-certo"));
    MockServerWebExchange exchange = exchange(null);
    boolean[] flag = new boolean[] {false};

    StepVerifier.create(filter.filter(exchange, chainQueMarca(flag))).verifyComplete();

    assertThat(flag[0]).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @DisplayName("autenticacao fica disponivel no contexto reativo apos chave valida")
  @Test
  void chaveValidaPopulaContextoReativo() {
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(propsComChave("segredo-certo"));
    MockServerWebExchange exchange = exchange("segredo-certo");
    boolean[] autenticado = new boolean[] {false};

    DefaultWebFilterChain chain =
        new DefaultWebFilterChain(
            (ex) ->
                ReactiveSecurityContextHolder.getContext()
                    .doOnNext(ctx -> autenticado[0] = ctx.getAuthentication().isAuthenticated())
                    .switchIfEmpty(
                        Mono.defer(
                            () -> {
                              autenticado[0] = false;
                              return Mono.empty();
                            }))
                    .then(),
            java.util.List.of());

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(autenticado[0]).isTrue();
  }

  @DisplayName("chave divergente mesmo compartilhando o prefixo valido e rejeitada com 401")
  @Test
  void chaveDivergenteMesmoComPrefixoComumEhRejeitada() {
    ApiKeyAuthWebFilter filter = new ApiKeyAuthWebFilter(propsComChave("segredo-certo"));
    MockServerWebExchange exchange = exchange("segredo-certo-mesmo-prefixo-mas-errado");
    boolean[] flag = new boolean[] {false};

    StepVerifier.create(filter.filter(exchange, chainQueMarca(flag))).verifyComplete();

    assertThat(flag[0]).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private static String corpo(MockServerWebExchange exchange) {
    return exchange.getResponse().getBodyAsString().block();
  }
}
