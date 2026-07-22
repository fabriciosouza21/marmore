package com.marmore.api.imageedit.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

/**
 * Constroi os eventos SSE ({@link ServerSentEvent}) do fluxo de edicao de imagem serializando os
 * payloads JSON via {@link ObjectMapper} (Jackson 3). Nunca por concatenacao de strings: isso
 * quebraria com {@link BigDecimal} em notacao cientifica (ex.: {@code 0.053000} viria {@code
 * 5.3E-2}) e nao escaparia aspas em mensagens de erro.
 *
 * <p>Cada payload JSON e modelado por um record privado, serializado pelo {@link ObjectWriter}
 * compartilhado. Os nomes de campo sao fixados por {@link JsonProperty} nos componentes do record
 * (snake_case), de modo que o JSON de saida nao dependa da configuracao global do {@link
 * ObjectMapper} injetado pelo Spring. Excecoes:
 *
 * <ul>
 *   <li>{@link #ping()} -- sem {@code data}.
 *   <li>{@link #imagem(String)} -- {@code data} e o base64 cru (sem envelope JSON), conforme
 *       PlantUML {@code data: <base64 PNG puro>}.
 * </ul>
 *
 * <p>Componente Spring (nao estatico) porque precisa do {@link ObjectMapper} injetado pelo Spring.
 */
@Component
public class SseEvents {

  private final ObjectWriter jsonWriter;

  /**
   * Construtor.
   *
   * @param mapper Jackson 3 {@link ObjectMapper} injetado pelo Spring
   */
  public SseEvents(ObjectMapper mapper) {
    this.jsonWriter = mapper.writer();
  }

  /**
   * Evento de fase do processamento. Payload JSON: {@code {"fase":"..."}}.
   *
   * @param fase nome da fase atual (ex.: {@code "processando"})
   */
  public ServerSentEvent<Object> status(String fase) {
    return ServerSentEvent.builder().data(toJson(new StatusPayload(fase))).build();
  }

  /** Evento de keepalive, sem {@code data}. */
  public ServerSentEvent<Object> ping() {
    return ServerSentEvent.builder().event("ping").build();
  }

  /**
   * Evento de conclusao com metricas. Payload JSON: {@code {"latency_ms":...,"custo_brl":...,
   * "usage":...}}. {@code usage} pode ser {@code null}; serializa como {@code null}.
   *
   * @param latencyMs latencia em milissegundos
   * @param custoBrl custo em BRL (BigDecimal preserva escala)
   * @param usage JSON cru de usage retornado pelo provedor, ou {@code null}
   */
  public ServerSentEvent<Object> done(long latencyMs, BigDecimal custoBrl, JsonNode usage) {
    return ServerSentEvent.builder()
        .data(toJson(new DonePayload(latencyMs, custoBrl, usage)))
        .build();
  }

  /**
   * Evento de imagem final. {@code data} e o base64 cru, sem envelope JSON.
   *
   * @param b64 imagem codificada em base64
   */
  public ServerSentEvent<Object> imagem(String b64) {
    return ServerSentEvent.builder().data(b64).build();
  }

  /**
   * Evento de erro. Payload JSON: {@code {"error":"...","latency_ms":...}}. A mensagem e escapada
   * (aspas, barras, etc.) pelo Jackson.
   *
   * @param error mensagem de erro
   * @param latencyMs latencia em milissegundos ate o erro
   */
  public ServerSentEvent<Object> error(String error, long latencyMs) {
    return ServerSentEvent.builder().data(toJson(new ErrorPayload(error, latencyMs))).build();
  }

  /** Serializa o payload via Jackson; erros de serializacao viram {@link IllegalStateException}. */
  private String toJson(Object payload) {
    try {
      return jsonWriter.writeValueAsString(payload);
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("falha ao serializar payload SSE: " + payload, e);
    }
  }

  /** Payload de {@link #status(String)}: {@code {"fase":"..."}}. */
  private record StatusPayload(String fase) {}

  /** Payload de {@link #done(long, BigDecimal, JsonNode)}. */
  private record DonePayload(
      @JsonProperty("latency_ms") long latencyMs,
      @JsonProperty("custo_brl") BigDecimal custoBrl,
      @JsonProperty("usage") JsonNode usage) {}

  /** Payload de {@link #error(String, long)}. */
  private record ErrorPayload(
      @JsonProperty("error") String error, @JsonProperty("latency_ms") long latencyMs) {}
}
