package com.marmore.api.imageedit.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Edição de imagem via OpenAI {@code /v1/images/edits} com {@code stream=true} (SSE).
 *
 * <p>O evento terminal deste endpoint é {@code image_edit.completed} — {@code
 * image_generation.completed} pertence a {@code /v1/images/generations}. O casamento aqui é feito
 * pelo sufixo {@code .completed}, então a classe serve para os dois endpoints.
 *
 * <p>Jackson 3 ({@code tools.jackson}): {@code asText()} virou {@code asString()}, {@code
 * fieldNames()} virou {@code propertyNames()} (agora {@code Collection}), e as exceções são
 * unchecked ({@link JacksonException} estende {@code RuntimeException}).
 */
public class OpenAiWebClientImageEditModel implements ImageEditModel {

  private static final Logger log = LoggerFactory.getLogger(OpenAiWebClientImageEditModel.class);

  private static final String EDITS_PATH = "/v1/images/edits";

  /** Sufixo do evento terminal: image_edit.completed OU image_generation.completed. */
  private static final String COMPLETED_SUFFIX = ".completed";

  /** Quantos caracteres do data aparecem no log. O payload real tem megabytes. */
  private static final int PREVIEW = 180;

  /** Jackson 3: JsonMapper no lugar de new ObjectMapper(). JsonMapper.shared() também serve. */
  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;

  /** Constroi o modelo com o {@link WebClient} configurado para o endpoint de edicao. */
  public OpenAiWebClientImageEditModel(WebClient webClient) {
    this.webClient = webClient;
  }

  // ---------------------------------------------------------------------
  // Fluxo
  // ---------------------------------------------------------------------

  @Override
  public Mono<ImageResponse> call(ImageEditPrompt prompt) {
    // defer: cada assinatura recebe seu próprio cronômetro e sua própria lista de eventos.
    return Mono.defer(
        () -> {
          final String correlacao = UUID.randomUUID().toString().substring(0, 8);
          final long inicio = System.nanoTime();
          final List<String> tiposVistos = new ArrayList<>(); // 1 assinatura = 1 thread

          return webClient
              .post()
              .uri(EDITS_PATH)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .accept(MediaType.TEXT_EVENT_STREAM)
              .bodyValue(multipartDe(prompt, correlacao))
              .retrieve()
              .bodyToFlux(SSE_STRING)
              // Registra todo evento e transforma erro-dentro-do-200 em exceção.
              .<ServerSentEvent<String>>handle(
                  (evt, sink) -> {
                    String tipo = tipoDe(evt);
                    tiposVistos.add(tipo);
                    log.debug(
                        "[{}] evento SSE tipo={} chars={} preview={}",
                        correlacao,
                        tipo.isBlank() ? "(sem type)" : tipo,
                        evt.data() == null ? 0 : evt.data().length(),
                        preview(evt.data()));
                    if (isFalha(tipo)) {
                      sink.error(
                          new AiImageException(
                              "OpenAI sinalizou erro no stream (tipo=%s): %s"
                                  .formatted(tipo, preview(evt.data()))));
                      return;
                    }
                    sink.next(evt);
                  })
              .filter(OpenAiWebClientImageEditModel::isCompleted)
              // .next() cancela o stream assim que o terminal chega. Isso gera aquele
              // "IllegalReferenceCountException: refCnt: 0" no log do reactor-netty — é
              // ruído do cancelamento, não perda de dado. Para silenciar, registre uma vez
              // no boot: Hooks.onErrorDropped(e -> log.debug("descartado no cancel", e)).
              // A alternativa .takeLast(1) evita o ruído, mas só completa quando a OpenAI
              // fecha a conexão — se ela mantiver keep-alive, você espera até o timeout.
              .next()
              .map(evt -> respostaDe(evt, correlacao))
              .switchIfEmpty(
                  Mono.defer(
                      () ->
                          Mono.error(
                              new AiImageException(
                                  "stream SSE terminou sem evento *%s; eventos recebidos=%s"
                                      .formatted(COMPLETED_SUFFIX, tiposVistos)))))
              .doOnSuccess(r -> log.info("[{}] edição concluída em {} ms", correlacao, ms(inicio)))
              .doOnError(e -> logFalha(correlacao, inicio, tiposVistos, e))
              // Um único ponto de tradução de exceção, preservando a causa.
              .onErrorMap(
                  e -> !(e instanceof AiImageException),
                  e ->
                      new AiImageException(
                          e.getClass().getSimpleName() + ": " + e.getMessage(), e));
        });
  }

  // ---------------------------------------------------------------------
  // Eventos
  // ---------------------------------------------------------------------

  private static boolean isCompleted(ServerSentEvent<String> evt) {
    return tipoDe(evt).endsWith(COMPLETED_SUFFIX);
  }

  private static boolean isFalha(String tipo) {
    return "error".equals(tipo) || tipo.endsWith(".failed") || tipo.endsWith(".error");
  }

  /**
   * Tipo do evento. Usa a linha {@code event:} do SSE quando presente (barato) e só cai para o
   * parse do JSON quando ela falta — evitando desserializar megabytes de base64 à toa.
   */
  private static String tipoDe(ServerSentEvent<String> evt) {
    if (evt.event() != null && !evt.event().isBlank()) {
      return evt.event();
    }
    String data = evt.data();
    if (data == null || data.isBlank()) {
      return "";
    }
    try {
      JsonNode node = MAPPER.readTree(data);
      return node == null ? "" : node.path("type").asString("");
    } catch (JacksonException e) {
      log.debug("não consegui extrair o type do evento SSE", e);
      return "";
    }
  }

  /**
   * Traduz o evento terminal em {@link ImageResponse}. Aceita {@code image_b64} (streaming novo) e
   * {@code b64_json} (legado).
   */
  private static ImageResponse respostaDe(ServerSentEvent<String> evt, String correlacao) {
    JsonNode node;
    try {
      node = MAPPER.readTree(evt.data());
    } catch (JacksonException e) {
      throw new AiImageException("JSON inválido no evento terminal: " + e.getMessage(), e);
    }
    if (node == null) {
      throw new AiImageException("evento terminal sem data");
    }

    JsonNode b64Node = node.path("image_b64");
    if (b64Node.isMissingNode() || b64Node.isNull()) {
      b64Node = node.path("b64_json");
    }
    if (b64Node.isMissingNode() || b64Node.isNull()) {
      throw new AiImageException("evento terminal sem image_b64/b64_json; campos=" + campos(node));
    }

    String b64 = b64Node.asString("");
    if (b64.isBlank()) {
      throw new AiImageException("evento terminal com image_b64/b64_json vazio");
    }
    log.info(
        "[{}] imagem recebida: base64={} chars (~{} KB decodificados) formato={} usage={}",
        correlacao,
        b64.length(),
        b64.length() * 3L / 4 / 1024,
        node.path("output_format").asString("?"),
        node.has("usage") ? node.get("usage") : "n/d");

    List<ImageGeneration> generations = new ArrayList<>();
    generations.add(ImageGeneration.of(Image.of(b64)));
    JsonNode usage = node.has("usage") ? node.get("usage") : null;
    return new ImageResponse(generations, new ImageResponseMetadata(usage), node);
  }

  // ---------------------------------------------------------------------
  // Multipart
  // ---------------------------------------------------------------------

  private static MultiValueMap<String, Object> multipartDe(
      ImageEditPrompt prompt, String correlacao) {
    final AiImageOptions opts = prompt.options();
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("prompt", prompt.instructions());
    body.add("stream", "true");
    body.add("partial_images", "0"); // sem parciais: cada uma seria outro evento de MBs
    if (opts.model() != null) {
      body.add("model", opts.model());
    }
    if (opts.n() != null) {
      body.add("n", opts.n());
    }
    if (opts.size() != null) {
      body.add("size", opts.size());
    }
    if (opts.quality() != null) {
      body.add("quality", opts.quality());
    }
    if (opts.sendsFidelity()) {
      body.add("input_fidelity", opts.inputFidelity());
    }

    long totalBytes = 0;
    for (InputImage img : prompt.inputImages()) {
      totalBytes += img.bytes().length;
      body.add("image[]", new NamedBytesResource(img.bytes(), img.filename()));
    }

    log.info(
        "[{}] POST {} model={} size={} quality={} imagens={} entrada={} KB",
        correlacao,
        EDITS_PATH,
        opts.model(),
        opts.size(),
        opts.quality(),
        prompt.inputImages().size(),
        totalBytes / 1024);
    return body;
  }

  // ---------------------------------------------------------------------
  // Logging
  // ---------------------------------------------------------------------

  private static void logFalha(
      String correlacao, long inicio, List<String> tiposVistos, Throwable e) {
    if (e instanceof WebClientResponseException w) {
      // getResponseBodyAsString() é sempre vazio em resposta streamada — o que importa
      // aqui é getMessage(), que traz o "but response failed with cause: ...".
      log.error(
          "[{}] edição falhou após {} ms: status={} uri={} eventos={} msg={}",
          correlacao,
          ms(inicio),
          w.getStatusCode(),
          w.getRequest() != null ? w.getRequest().getURI() : EDITS_PATH,
          tiposVistos,
          w.getMessage(),
          w);
    } else {
      log.error(
          "[{}] edição falhou após {} ms: eventos={}", correlacao, ms(inicio), tiposVistos, e);
    }
  }

  private static long ms(long inicioNanos) {
    return Duration.ofNanos(System.nanoTime() - inicioNanos).toMillis();
  }

  private static String preview(String data) {
    if (data == null) {
      return "(null)";
    }
    return data.length() <= PREVIEW ? data : data.substring(0, PREVIEW) + "…";
  }

  /** Jackson 3: propertyNames() devolve Collection, não Iterator. */
  private static String campos(JsonNode node) {
    return String.valueOf(node.propertyNames());
  }

  /** ByteArrayResource com nome de arquivo, necessário para multipart. */
  private static final class NamedBytesResource extends ByteArrayResource {
    private final String filename;

    NamedBytesResource(byte[] bytes, String filename) {
      super(bytes);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
