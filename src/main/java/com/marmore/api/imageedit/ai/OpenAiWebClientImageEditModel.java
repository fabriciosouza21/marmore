package com.marmore.api.imageedit.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gateway reativo de edicao de imagem baseado em {@link WebClient}. Implementacao do contrato
 * {@link ImageEditModel} que consome o endpoint SSE da OpenAI {@code POST /v1/images/edits} com
 * {@code stream=true} e {@code partial_images=0}.
 *
 * <p>Com {@code partial_images=0}, a OpenAI emite apenas o evento terminal {@code
 * image_generation.completed} (alem de eventos nao relacionados a imagem, como {@code ping}). Este
 * gateway filtra o stream ate esse evento, extrai a imagem em base64 e o {@code usage}, e completa
 * o {@link Mono} com a {@link ImageResponse}. Falhas (HTTP 4xx/5xx, stream sem o evento terminal,
 * ou JSON malformado) viram {@link AiImageException} (unchecked).
 *
 * <h2>Abordagem de parse SSE</h2>
 *
 * <p>Usa o decodificador nativo do WebFlux via {@code bodyToFlux(new
 * ParameterizedTypeReference<ServerSentEvent<String>>() {})}. O {@code
 * ServerSentEventHttpMessageReader} do Spring parseia o framing SSE (linhas {@code event:}/{@code
 * data:} separadas por linha em branco) em eventos estruturados, lidando com multi-linha e
 * delimitacao de forma robusta. Filtramos pelo {@code event() == "image_generation.completed"} (com
 * fallback defensivo: se a OpenAI omitir o campo {@code event:}, identificamos o evento pelo campo
 * {@code type} dentro do JSON do {@code data}). E a opcao mais idiomatica em WebClient
 * (alternativas: {@code bodyToFlux(String.class)} de linhas ou de chunks delimitados, ambas mais
 * frageis a framing parcial).
 *
 * <h2>Ambiguidade do nome do campo da imagem (risco documentado)</h2>
 *
 * <p>O campo da imagem no evento {@code image_generation.completed} pode ser {@code image_b64}
 * (formato de streaming novo) ou {@code b64_json} (formato sincrono legado). Sem uma chave OpenAI
 * viva nos testes, o parser tenta ambos defensivamente ({@code image_b64} primeiro, {@code
 * b64_json} como fallback). Valide contra uma resposta capturada da OpenAI em staging.
 *
 * <h2>Decisao: stream sem evento terminal</h2>
 *
 * <p>Se o stream completa sem nunca emitir um {@code image_generation.completed}, o {@link Mono}
 * completa vazio ({@code onComplete} sem {@code onNext}). Traduzimos isso em erro via {@code
 * switchIfEmpty}: o chamador recebe {@link AiImageException} em vez de um {@code null} silencioso,
 * porque a ausencia do evento terminal indica falha de protocolo, nao sucesso.
 *
 * <h2>Wiring</h2>
 *
 * <p>NAO anotada com {@code @Component}: o bean {@code WebClient} ({@code imageWebClient}) e criado
 * na Task 10. A tarefa de wiring (instanciar este gateway e expor como bean, substituindo o antigo
 * {@code OpenAiRestClientImageEditModel}) e responsabilidade da Task 10, que tambem remove o
 * gateway sincrono legado.
 */
public class OpenAiWebClientImageEditModel implements ImageEditModel {

  private static final String EDITS_PATH = "/v1/images/edits";
  private static final String COMPLETED_EVENT = "image_generation.completed";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_STRING =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;

  /**
   * Construtor.
   *
   * @param webClient cliente HTTP autenticado para a OpenAI (bean {@code imageWebClient} da Task
   *     10)
   */
  public OpenAiWebClientImageEditModel(WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<ImageResponse> call(ImageEditPrompt prompt) {
    return webClient
        .post()
        .uri(EDITS_PATH)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartDe(prompt))
        .retrieve()
        .bodyToFlux(SSE_STRING)
        .filter(OpenAiWebClientImageEditModel::isCompleted)
        .next()
        .map(OpenAiWebClientImageEditModel::respostaDe)
        .switchIfEmpty(
            Mono.error(new AiImageException("stream SSE sem evento image_generation.completed")))
        .onErrorResume(
            WebClientResponseException.class,
            e ->
                Mono.error(
                    new AiImageException(
                        e.getClass().getSimpleName()
                            + " ["
                            + e.getStatusCode()
                            + "]: "
                            + e.getResponseBodyAsString(),
                        e)))
        .onErrorResume(
            e -> !(e instanceof AiImageException),
            e ->
                Mono.error(
                    new AiImageException(e.getClass().getSimpleName() + ": " + e.getMessage(), e)));
  }

  /**
   * Identifica o evento terminal. Confia no campo {@code event()} do SSE quando presente; como
   * fallback defensivo, inspeciona o campo {@code type} dentro do JSON do {@code data} (alguns
   * provedores/situacoes omitem a linha {@code event:}).
   */
  private static boolean isCompleted(ServerSentEvent<String> evt) {
    if (COMPLETED_EVENT.equals(evt.event())) {
      return true;
    }
    String data = evt.data();
    if (data == null || data.isBlank()) {
      return false;
    }
    try {
      JsonNode node = MAPPER.readTree(data);
      return node != null && COMPLETED_EVENT.equals(node.path("type").asText());
    } catch (Exception e) {
      return false;
    }
  }

  /** Monta o multipart a partir do prompt, preservando a ordem das imagens de entrada. */
  private static MultiValueMap<String, Object> multipartDe(ImageEditPrompt prompt) {
    final AiImageOptions opts = prompt.options();
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("prompt", prompt.instructions());
    body.add("stream", "true");
    body.add("partial_images", "0");
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
    for (InputImage img : prompt.inputImages()) {
      body.add("image[]", new NamedBytesResource(img.bytes(), img.filename()));
    }
    return body;
  }

  /**
   * Traduz o JSON do {@code data} do evento terminal em {@link ImageResponse}. Tenta o campo {@code
   * image_b64} (streaming novo) e cai para {@code b64_json} (legado). Lanca em resposta malformada.
   */
  private static ImageResponse respostaDe(ServerSentEvent<String> evt) {
    JsonNode node;
    try {
      node = MAPPER.readTree(evt.data());
    } catch (Exception e) {
      throw new AiImageException(
          "JSON invalido no evento image_generation.completed: " + e.getMessage(), e);
    }
    if (node == null) {
      throw new AiImageException("evento image_generation.completed sem data");
    }
    // Ambiguidade documentada: image_b64 (novo) vs b64_json (legado).
    JsonNode b64Node = node.path("image_b64");
    if (b64Node.isMissingNode() || b64Node.isNull()) {
      b64Node = node.path("b64_json");
    }
    if (b64Node.isMissingNode() || b64Node.isNull()) {
      throw new AiImageException("evento image_generation.completed sem image_b64/b64_json");
    }
    List<ImageGeneration> generations = new ArrayList<>();
    generations.add(ImageGeneration.of(Image.of(b64Node.asText())));
    JsonNode usage = node.has("usage") ? node.get("usage") : null;
    ImageResponseMetadata metadata = new ImageResponseMetadata(usage);
    return new ImageResponse(generations, metadata, node);
  }

  /** ByteArrayResource com nome de arquivo, necessario para multipart. */
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
