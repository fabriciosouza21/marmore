package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testes de {@link SseEvents}. Valida por PARSE-REVERSO: pega o {@code data} do {@link
 * ServerSentEvent}, faz parse via {@link ObjectMapper#readTree(String)} e asserta sobre os campos.
 * Isso prova que o JSON e valido (nao depende de formatacao exata) e protege contra o bug que
 * motivou o componente: {@link BigDecimal} em notacao cientifica e strings nao escapadas.
 */
class SseEventsTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private SseEvents events;

  @BeforeEach
  void setUp() {
    events = new SseEvents(MAPPER);
  }

  @DisplayName("status produz JSON valido com a fase (parse reverso)")
  @Test
  void statusProduzJsonComFase() {
    ServerSentEvent<Object> sse = events.status("processando");

    JsonNode node = parse(sse.data());
    assertThat(node.get("fase").asText()).isEqualTo("processando");
  }

  @DisplayName("ping retorna evento sem data")
  @Test
  void pingSemData() {
    ServerSentEvent<Object> sse = events.ping();

    assertThat(sse.data()).isNull();
  }

  @DisplayName("done produz JSON com latency_ms, custo_brl e usage (parse reverso)")
  @Test
  void doneComLatencyCustoUsage() {
    JsonNode usage = MAPPER.createObjectNode().put("prompt_tokens", 42);

    ServerSentEvent<Object> sse = events.done(150L, new BigDecimal("0.053000"), usage);

    JsonNode node = parse(sse.data());
    assertThat(node.get("latency_ms").asLong()).isEqualTo(150L);
    assertThat(node.get("custo_brl").decimalValue())
        .isEqualByComparingTo(new BigDecimal("0.053000"));
    assertThat(node.get("usage").get("prompt_tokens").asInt()).isEqualTo(42);
  }

  @DisplayName("done com usage=null serializa o campo como null")
  @Test
  void doneComUsageNuloSerializaComoNull() {
    ServerSentEvent<Object> sse = events.done(50L, new BigDecimal("0.01"), null);

    JsonNode node = parse(sse.data());
    assertThat(node.has("usage")).isTrue();
    assertThat(node.get("usage").isNull()).isTrue();
  }

  @DisplayName("custo_brl BigDecimal preserva escala (0.053000 NAO vira 5.3E-2)")
  @Test
  void custoBrlPreservaEscala() {
    BigDecimal valor = new BigDecimal("0.053000");

    ServerSentEvent<Object> sse = events.done(1L, valor, null);

    // O token numerico no JSON cru preserva a escala literal "0.053000", e nao "0.053" (Double)
    // nem "5.3E-2". Isto documenta a razao de usar Jackson sobre concatenacao com Double.
    JsonNode node = parse(sse.data());
    String tokenNumerico = tokenNumerico((String) sse.data(), "custo_brl");
    assertThat(tokenNumerico).isEqualTo("0.053000");
    assertThat(tokenNumerico).doesNotContain("E").doesNotContain("e");
    assertThat(node.get("custo_brl").decimalValue()).isEqualByComparingTo(valor);
  }

  @DisplayName("imagem retorna o base64 cru como data (sem envelope JSON)")
  @Test
  void imagemRetornaBase64Cru() {
    // base64 curto e valido (nao precisa decodificar para uma imagem real).
    String b64 = "aGVsbG8=";

    ServerSentEvent<Object> sse = events.imagem(b64);

    assertThat(sse.data()).isEqualTo(b64);
  }

  @DisplayName("error escapa aspas na mensagem (parse reverso)")
  @Test
  void errorEscapaAspasNaMensagem() {
    String mensagem = "falha com \"aspas\" no meio";

    ServerSentEvent<Object> sse = events.error(mensagem, 99L);

    JsonNode node = parse(sse.data());
    assertThat(node.get("error").asText()).isEqualTo(mensagem);
    assertThat(node.get("latency_ms").asLong()).isEqualTo(99L);
  }

  @DisplayName("error produz JSON com error e latency_ms (parse reverso)")
  @Test
  void errorIncluiMensagemLatency() {
    ServerSentEvent<Object> sse = events.error("boom", 7L);

    JsonNode node = parse(sse.data());
    assertThat(node.get("error").asText()).isEqualTo("boom");
    assertThat(node.get("latency_ms").asLong()).isEqualTo(7L);
  }

  @DisplayName("status escapa aspas na fase (string e escapada)")
  @Test
  void statusEscapaAspasNaFase() {
    String fase = "ok \"mesmo\"";

    ServerSentEvent<Object> sse = events.status(fase);

    JsonNode node = parse(sse.data());
    assertThat(node.get("fase").asText()).isEqualTo(fase);
  }

  /** Faz parse reverso do data, provando que o JSON e valido. */
  private static JsonNode parse(Object data) {
    assertThat(data).as("data do SSE").isInstanceOf(String.class);
    try {
      return MAPPER.readTree((String) data);
    } catch (tools.jackson.core.JacksonException e) {
      throw new AssertionError("data nao e JSON valido: " + data, e);
    }
  }

  /**
   * Extrai o token numerico bruto que segue o campo {@code fieldName} no JSON cru, para validar
   * notacao literal (ex.: preservar escala de {@link BigDecimal}) sem depender do parse, que
   * normaliza trailing zeros.
   */
  private static String tokenNumerico(String json, String fieldName) {
    int i = json.indexOf("\"" + fieldName + "\"");
    assertThat(i).as("campo %s presente no JSON", fieldName).isGreaterThan(-1);
    int colon = json.indexOf(':', i);
    int start = colon + 1;
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
      start++;
    }
    int end = start;
    while (end < json.length() && "0123456789.+-eE".indexOf(json.charAt(end)) >= 0) {
      end++;
    }
    return json.substring(start, end);
  }
}
