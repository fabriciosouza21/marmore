package com.marmore.api.imageedit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marmore.api.imageedit.domain.GenerateResult;
import com.marmore.api.imageedit.service.ImageEditService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Testes do {@link ImageEditHandler}. Foco na composicao reativa: a sequencia SSE (status x3 ->
 * done -> imagem OU error), e o heartbeat (ping a cada 15s) que precisa PARAR quando o resultado
 * chega.
 *
 * <p>O {@link ImageEditService} e mock para isolar o comportamento do handler. O {@link SseEvents}
 * e real (serializacao real via Jackson) para validar o contrato dos eventos. O metodo
 * package-private {@link ImageEditHandler#stream(byte[])} e exercido diretamente com {@link
 * StepVerifier#withVirtualTime} para avancar o relogio sem esperar 15s reais.
 */
class ImageEditHandlerTest {

  private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder().build();
  private static final byte[] BYTES = new byte[] {1, 2, 3, 4, 5};

  private ImageEditService service;
  private SseEvents events;
  private ImageEditHandler handler;

  @BeforeEach
  void setUp() {
    service = mock(ImageEditService.class);
    events = new SseEvents(MAPPER);
    handler = new ImageEditHandler(service, events);
  }

  @DisplayName("sucesso: resultado síncrono emite status×3 -> done -> imagem, sem ping")
  @Test
  void sucessoSincronoEmiteStatusDoneImagemSemPing() {
    when(service.generate(any(byte[].class))).thenReturn(Mono.just(okResult()));

    StepVerifier.create(handler.stream(BYTES))
        .expectNextMatches(isStatus("recebido"))
        .expectNextMatches(isStatus("redimensionando"))
        .expectNextMatches(isStatus("gerando"))
        .expectNextMatches(isDone())
        .expectNextMatches(isImagem("aGVsbG8="))
        .verifyComplete();
  }

  @DisplayName("erro de domínio emite status×3 -> error (sem done, sem imagem)")
  @Test
  void erroDeDominioEmiteStatusError() {
    when(service.generate(any(byte[].class)))
        .thenReturn(Mono.just(new GenerateResult.Err("OPENAI_API_KEY ausente", 5L)));

    StepVerifier.create(handler.stream(BYTES))
        .expectNextMatches(isStatus("recebido"))
        .expectNextMatches(isStatus("redimensionando"))
        .expectNextMatches(isStatus("gerando"))
        .expectNextMatches(isError("OPENAI_API_KEY ausente"))
        .verifyComplete();
  }

  @DisplayName("heartbeat: ping emitido a cada 15s enquanto a geração demora")
  @Test
  void heartbeatEmitePingEnquantoGeracaoDemora() {
    when(service.generate(any(byte[].class)))
        .thenReturn(Mono.delay(Duration.ofSeconds(50)).thenReturn(okResult()));

    StepVerifier.withVirtualTime(() -> handler.stream(BYTES))
        .expectNextMatches(isStatus("recebido"))
        .expectNextMatches(isStatus("redimensionando"))
        .expectNextMatches(isStatus("gerando"))
        .thenAwait(Duration.ofSeconds(15))
        .expectNextMatches(isPing())
        .thenAwait(Duration.ofSeconds(15))
        .expectNextMatches(isPing())
        .thenAwait(Duration.ofSeconds(15))
        .expectNextMatches(isPing())
        .thenAwait(Duration.ofSeconds(5))
        .expectNextMatches(isDone())
        .expectNextMatches(isImagem("aGVsbG8="))
        .verifyComplete();
  }

  @DisplayName("heartbeat para no resultado: nenhum ping após done/imagem")
  @Test
  void heartbeatParaImediatamenteQuandoResultadoChega() {
    when(service.generate(any(byte[].class)))
        .thenReturn(Mono.delay(Duration.ofSeconds(20)).thenReturn(okResult()));

    StepVerifier.withVirtualTime(() -> handler.stream(BYTES))
        .expectNextMatches(isStatus("recebido"))
        .expectNextMatches(isStatus("redimensionando"))
        .expectNextMatches(isStatus("gerando"))
        .thenAwait(Duration.ofSeconds(15))
        .expectNextMatches(isPing())
        .thenAwait(Duration.ofSeconds(5))
        .expectNextMatches(isDone())
        .expectNextMatches(isImagem("aGVsbG8="))
        .thenAwait(Duration.ofSeconds(60))
        .expectComplete()
        .verify();
  }

  @DisplayName("FilePart 'image' ausente -> 400 BAD_REQUEST")
  @Test
  void filePartAusenteRetorna400() {
    ServerRequest request = mock(ServerRequest.class);
    when(request.multipartData()).thenReturn(Mono.just(new LinkedMultiValueMap<>()));

    StepVerifier.create(handler.edit(request))
        .expectNextMatches(r -> r.statusCode().value() == 400)
        .verifyComplete();
  }

  @DisplayName("edit() com FilePart 'image' presente delega para stream() em body SSE")
  @Test
  void editComFilePartPresenteDevolve200ComSse() {
    FilePart filePart = mock(FilePart.class);
    when(filePart.content()).thenReturn(Flux.just(bufferDe(BYTES)));
    when(service.generate(any(byte[].class))).thenReturn(Mono.just(okResult()));
    FormFieldPart pedra = mock(FormFieldPart.class);
    when(pedra.value()).thenReturn("verde_ubatuba");
    ServerRequest request = mock(ServerRequest.class);
    when(request.multipartData())
        .thenReturn(
            Mono.just(
                new LinkedMultiValueMap<>(
                    Map.of(
                        "image", java.util.List.of(filePart), "pedra", java.util.List.of(pedra)))));

    ServerResponse response = handler.edit(request).block();

    assertThat(response).isNotNull();
    assertThat(response.statusCode().is2xxSuccessful()).isTrue();
    assertThat(response.headers().getContentType())
        .isNotNull()
        .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue());
  }

  private static GenerateResult.Ok okResult() {
    JsonNode usage = MAPPER.createObjectNode().put("total_tokens", 100);
    return new GenerateResult.Ok("aGVsbG8=", usage, 200L, null);
  }

  private static DataBuffer bufferDe(byte[] bytes) {
    DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.allocateBuffer(bytes.length);
    buffer.write(bytes);
    return buffer;
  }

  private static java.util.function.Predicate<ServerSentEvent<Object>> isStatus(String fase) {
    return sse -> {
      Object data = sse.data();
      if (!(data instanceof String s)) {
        return false;
      }
      try {
        return fase.equals(MAPPER.readTree(s).get("fase").asText());
      } catch (tools.jackson.core.JacksonException e) {
        return false;
      }
    };
  }

  private static java.util.function.Predicate<ServerSentEvent<Object>> isPing() {
    return sse -> "ping".equals(sse.event()) && sse.data() == null;
  }

  private static java.util.function.Predicate<ServerSentEvent<Object>> isDone() {
    return sse -> {
      Object data = sse.data();
      if (!(data instanceof String s)) {
        return false;
      }
      try {
        JsonNode node = MAPPER.readTree(s);
        return node.has("latency_ms") && node.has("custo_brl") && node.has("usage");
      } catch (tools.jackson.core.JacksonException e) {
        return false;
      }
    };
  }

  private static java.util.function.Predicate<ServerSentEvent<Object>> isImagem(String b64) {
    return sse -> sse.event() == null && b64.equals(sse.data());
  }

  private static java.util.function.Predicate<ServerSentEvent<Object>> isError(String error) {
    return sse -> {
      Object data = sse.data();
      if (!(data instanceof String s)) {
        return false;
      }
      try {
        JsonNode node = MAPPER.readTree(s);
        return error.equals(node.get("error").asText()) && node.has("latency_ms");
      } catch (tools.jackson.core.JacksonException e) {
        return false;
      }
    };
  }
}
