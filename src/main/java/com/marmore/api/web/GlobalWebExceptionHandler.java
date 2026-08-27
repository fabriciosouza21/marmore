package com.marmore.api.web;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Tratador global de excecoes reativo ({@link WebExceptionHandler}). Traduz excecoes em respostas
 * JSON {@code {"error":"..."}} preservando a semantica de status:
 *
 * <ul>
 *   <li>{@link ResponseStatusException} e {@link ErrorResponseException} -> {@link
 *       ErrorResponseException#getStatusCode()}.
 *   <li>Qualquer outra -> {@link HttpStatus#INTERNAL_SERVER_ERROR} com mensagem generica ("erro
 *       interno"), evitando vazar stacktrace/detalhe interno.
 * </ul>
 *
 * <h3>Ordenacao</h3>
 *
 * <p>O {@code WebHttpHandlerBuilder} do Spring encadeia os {@link WebExceptionHandler} em ordem
 * <strong>ascendente</strong> (valor menor = prioridade maior = roda primeiro). O tratador padrao
 * {@code ResponseStatusExceptionHandler} e registrado com prioridade baixa (rodando por ultimo).
 * {@link #ORDER} negativo ({@value #ORDER}) garante que este handler roda antes do padrao,
 * interceptando as excecoes de dominio e devolvendo o JSON no formato do projeto em vez do
 * ProblemDetail default do Spring.
 */
@Component
@Order(GlobalWebExceptionHandler.ORDER)
public class GlobalWebExceptionHandler implements WebExceptionHandler, Ordered {

  /**
   * Ordem do handler. Negativa para rodar antes do {@code ResponseStatusExceptionHandler} padrao
   * (que tem prioridade baixa / valor alto).
   */
  public static final int ORDER = -2;

  private static final byte[] CORPO_500 =
      "{\"error\":\"erro interno\"}".getBytes(StandardCharsets.UTF_8);

  private final ObjectMapper mapper;

  /**
   * Construtor.
   *
   * @param mapper Jackson 3 {@link ObjectMapper} injetado pelo Spring, usado para serializar o body
   *     JSON de erro (nunca por concatenacao de strings)
   */
  public GlobalWebExceptionHandler(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (ex instanceof ResponseStatusException rse) {
      return responder(exchange, rse.getStatusCode(), rse.getReason());
    }
    if (ex instanceof ErrorResponseException ere) {
      return responder(exchange, ere.getStatusCode(), mensagemDe(ere));
    }
    return responderGenerico(exchange);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  /**
   * Escreve a resposta JSON {@code {"error":"<mensagem>"}} com o status dado. Se a mensagem for
   * {@code null}, usa o reason phrase do status para nao produzir JSON invalido.
   *
   * @param exchange exchange reativo
   * @param status status HTTP da resposta
   * @param mensagem mensagem de erro (pode ser {@code null})
   * @return Mono que completa ao escrever a resposta
   */
  private Mono<Void> responder(
      ServerWebExchange exchange, HttpStatusCode status, @Nullable String mensagem) {
    HttpStatus resolvido = HttpStatus.resolve(status.value());
    String texto =
        mensagem != null
            ? mensagem
            : (resolvido != null ? resolvido.getReasonPhrase() : "erro " + status.value());
    byte[] corpo = jsonError(texto);
    return escrever(exchange, status, corpo);
  }

  /** Resposta 500 generica (nao vaza detalhe interno da excecao). */
  private static Mono<Void> responderGenerico(ServerWebExchange exchange) {
    return escrever(exchange, HttpStatus.INTERNAL_SERVER_ERROR, CORPO_500);
  }

  /** Extrai mensagem de {@link ErrorResponseException} sem vazar null. */
  private static @Nullable String mensagemDe(ErrorResponseException ere) {
    String detail = ere.getBody().getDetail();
    return detail != null ? detail : ere.getMessage();
  }

  /**
   * Monta o body JSON {@code {"error":"<texto>"}} serializando o record {@link ErrorPayload} via
   * {@link ObjectMapper}. O Jackson trata o escapamento de aspas, barras e caracteres de controle
   * corretamente; concatenacao manual e anti-pattern do projeto.
   *
   * @param texto texto da mensagem (nao nulo)
   * @return bytes UTF-8 do JSON
   */
  private byte[] jsonError(String texto) {
    try {
      return mapper.writeValueAsBytes(new ErrorPayload(texto));
    } catch (JacksonException e) {
      // records simples nunca falham ao serializar; defesa em profundidade.
      throw new IllegalStateException("falha ao serializar payload de erro: " + texto, e);
    }
  }

  /** Payload de erro serializado pelo Jackson: {@code {"error":"..."}}. */
  private record ErrorPayload(String error) {}

  /** Escreve a resposta com status e body JSON, setando content-type e content-length. */
  private static Mono<Void> escrever(
      ServerWebExchange exchange, HttpStatusCode status, byte[] corpo) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    response.getHeaders().setContentLength(corpo.length);
    DataBufferFactory factory = response.bufferFactory();
    return response.writeWith(Mono.just(factory.wrap(corpo)));
  }
}
