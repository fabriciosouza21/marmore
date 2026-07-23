package com.marmore.api.imageedit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Testes de {@link OpenAiWebClientImageEditModel}: gateway reativo que consome o stream SSE da
 * OpenAI ({@code /v1/images/edits} com {@code stream=true} e {@code partial_images=0}). Valida
 * parse do evento {@code image_generation.completed} (campo da imagem e usage), erro HTTP 4xx/5xx,
 * e stream sem o evento terminal — todos via {@link MockWebServer}.
 */
class OpenAiWebClientImageEditModelTest {

  private static final String B64 = "aGVsbG8="; // "hello" em base64

  private MockWebServer server;
  private OpenAiWebClientImageEditModel model;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    model = new OpenAiWebClientImageEditModel(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @DisplayName("parseia evento completed com image_b64 e usage (formato streaming novo)")
  @Test
  void parseiaEventoCompletedComImagemUsage() throws InterruptedException {
    String data =
        "{\"type\":\"image_generation.completed\",\"image_b64\":\""
            + B64
            + "\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30}}";
    server.enqueue(sseCompleted(data));

    ImageResponse resp = model.call(promptFixo()).block();

    assertThat(resp).isNotNull();
    assertThat(resp.getResult()).isNotNull();
    assertThat(resp.getResult().output().b64Json()).isEqualTo(B64);
    assertThat(resp.metadata().usage()).isNotNull();
    assertThat(resp.metadata().usage().get("total_tokens").asInt()).isEqualTo(30);
  }

  @DisplayName("aceita formato legado com b64_json (defensive field-name)")
  @Test
  void aceitaFormatoLegadoComB64Json() {
    String data = "{\"type\":\"image_generation.completed\",\"b64_json\":\"" + B64 + "\"}";
    server.enqueue(sseCompleted(data));

    ImageResponse resp = model.call(promptFixo()).block();

    assertThat(resp).isNotNull();
    assertThat(resp.getResult().output().b64Json()).isEqualTo(B64);
    assertThat(resp.metadata().usage()).isNull();
  }

  @DisplayName("ignora eventos parciais e finaliza apenas no completed")
  @Test
  void ignoraParciaisFinalizaNoCompleted() {
    String partial1 =
        "event: image_generation.partial_image\ndata: {\"image_b64\":\"PARTIAL\"}\n\n";
    String partial2 = "event: ping\ndata: {}\n\n";
    String completed =
        "event: image_generation.completed\ndata: {\"type\":\"image_generation.completed\","
            + "\"image_b64\":\""
            + B64
            + "\"}\n\n";
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(partial1 + partial2 + completed));

    ImageResponse resp = model.call(promptFixo()).block();

    assertThat(resp).isNotNull();
    assertThat(resp.getResult().output().b64Json()).isEqualTo(B64);
  }

  @DisplayName(
      "envia multipart com stream=true, partial_images=0 e image[] na ordem (ambiente, pedra)")
  @Test
  void enviaMultipartComStreamOrdemImagens() throws InterruptedException {
    server.enqueue(
        sseCompleted("{\"type\":\"image_generation.completed\",\"image_b64\":\"" + B64 + "\"}"));

    model.call(promptComDuasImagens()).block();

    RecordedRequest req = server.takeRequest();
    String body = req.getBody().readUtf8();
    // O wire format do multipart usa CRLF e coloca nome e valor separados por headers; validamos os
    // nomes das partes (carga semantica) e os valores literais "true"/"0" sem depender do framing.
    assertThat(body)
        .contains("name=\"stream\"")
        .contains("name=\"partial_images\"")
        .contains("name=\"image[]\"")
        .contains("ambiente.jpg")
        .contains("pedra.png");
    assertThat(body).contains("true").contains("0");
    assertThat(req.getPath()).isEqualTo("/v1/images/edits");
    // Ordem ambiente -> pedra
    assertThat(body.indexOf("ambiente.jpg")).isLessThan(body.indexOf("pedra.png"));
  }

  @DisplayName(
      "envia no multipart as partes condicionais prompt/model/n/size/quality quando populadas")
  @Test
  void enviaMultipartComPartesCondicionaisPopuladas() throws InterruptedException {
    server.enqueue(
        sseCompleted("{\"type\":\"image_generation.completed\",\"image_b64\":\"" + B64 + "\"}"));

    model.call(promptFixo()).block();

    String body = server.takeRequest().getBody().readUtf8();
    assertThat(body)
        .contains("name=\"prompt\"")
        .contains("edite a imagem")
        .contains("name=\"model\"")
        .contains("gpt-image-2")
        .contains("name=\"n\"")
        .contains("name=\"size\"")
        .contains("1024x1024")
        .contains("name=\"quality\"")
        .contains("medium");
  }

  @DisplayName("erro HTTP 500 vira AiImageException")
  @Test
  void erroHttp500ViraAiImageException() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(() -> model.call(promptFixo()).block()).isInstanceOf(AiImageException.class);
  }

  @DisplayName("stream que termina sem evento completed vira AiImageException")
  @Test
  void streamSemCompletedViraAiImageException() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody("event: ping\ndata: {}\n\n"));

    assertThatThrownBy(() -> model.call(promptFixo()).block()).isInstanceOf(AiImageException.class);
  }

  @DisplayName("evento completed sem campo de imagem vira AiImageException")
  @Test
  void completedSemCampoDeImagemViraAiImageException() {
    server.enqueue(sseCompleted("{\"type\":\"image_generation.completed\"}"));

    assertThatThrownBy(() -> model.call(promptFixo()).block()).isInstanceOf(AiImageException.class);
  }

  private static MockResponse sseCompleted(String jsonData) {
    return new MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: image_generation.completed\ndata: " + jsonData + "\n\n");
  }

  private static ImageEditPrompt promptFixo() {
    return ImageEditPrompt.of(
        "edite a imagem",
        AiImageOptions.defaults(),
        List.of(InputImage.of(new byte[] {1}, "a.jpg")));
  }

  private static ImageEditPrompt promptComDuasImagens() {
    return ImageEditPrompt.of(
        "edite ambiente+pedra",
        AiImageOptions.defaults(),
        List.of(
            InputImage.of(new byte[] {1, 2}, "ambiente.jpg"),
            InputImage.of(new byte[] {3, 4}, "pedra.png")));
  }
}
