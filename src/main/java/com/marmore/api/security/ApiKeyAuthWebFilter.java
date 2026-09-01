package com.marmore.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filtro de autenticacao por API key (reativo). Le a chave do header {@value #HEADER} e compara com
 * a configurada em {@link ApiKeyProperties} via {@link MessageDigest#isEqual} (comparacao constante
 * no tempo, evita timing attack). Chave valida autentica o request no contexto reativo e continua a
 * chain; ausente ou invalida responde 401 JSON e interrompe.
 */
@Component
public class ApiKeyAuthWebFilter implements WebFilter {

  /** Nome do header HTTP que carrega a API key. */
  public static final String HEADER = "X-API-Key";

  /** Paths publicos (seguem a chain sem autenticar): healthcheck de liveness/versao. */
  private static final Set<String> PATHS_PUBLICOS = Set.of("/health");

  private static final byte[] CORPO_401 =
      "{\"error\":\"API key ausente ou invalida\"}".getBytes(StandardCharsets.UTF_8);

  private final ApiKeyProperties props;

  /**
   * Construtor.
   *
   * @param props propriedades com a chave esperada
   */
  public ApiKeyAuthWebFilter(ApiKeyProperties props) {
    this.props = props;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (PATHS_PUBLICOS.contains(exchange.getRequest().getPath().value())) {
      return chain.filter(exchange);
    }
    String fornecida = exchange.getRequest().getHeaders().getFirst(HEADER);
    String esperada = props.getKey();
    if (chaveConfere(fornecida, esperada)) {
      UsernamePasswordAuthenticationToken autenticacao =
          UsernamePasswordAuthenticationToken.authenticated("apikey", null, List.of());
      return chain
          .filter(exchange)
          .contextWrite(ReactiveSecurityContextHolder.withAuthentication(autenticacao));
    }
    return rejeitar(exchange);
  }

  private static boolean chaveConfere(@Nullable String fornecida, @Nullable String esperada) {
    if (fornecida == null || esperada == null) {
      return false;
    }
    return MessageDigest.isEqual(sha256(fornecida), sha256(esperada));
  }

  /**
   * Hash SHA-256 dos bytes da chave em UTF-8. Garante que ambas as entradas cheguem a {@link
   * MessageDigest#isEqual} com o mesmo tamanho (32 bytes), eliminando o oracle de tempo que vaza o
   * comprimento da chave quando os tamanhos diferem.
   */
  private static byte[] sha256(String valor) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(valor.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
    }
  }

  private static Mono<Void> rejeitar(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return exchange
        .getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(CORPO_401)));
  }
}
