package com.marmore.api.image.ai;

import com.marmore.api.imageedit.ai.AiImageException;
import com.marmore.api.imageedit.ai.AiImageOptions;
import com.marmore.api.imageedit.ai.Image;
import com.marmore.api.imageedit.ai.ImageEditPrompt;
import com.marmore.api.imageedit.ai.ImageGeneration;
import com.marmore.api.imageedit.ai.ImageResponse;
import com.marmore.api.imageedit.ai.ImageResponseMetadata;
import com.marmore.api.imageedit.ai.InputImage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Implementacao de {@link ImageEditModel} que fala com a OpenAI via {@code RestClient}. Migrada da
 * logica de rede que antes vivia em {@code ImageEditService}: monta o multipart para {@code
 * /v1/images/edits} preservando a ordem das imagens de entrada, faz o POST, e traduz a resposta em
 * {@link ImageResponse}. Falhas viram {@link AiImageException} (unchecked), nunca propagam excecoes
 * brutas.
 *
 * <p>A ordem de {@code image[]} segue {@link ImageEditPrompt#inputImages()}: o prompt fixo do
 * produto referencia IMAGE 1 (ambiente) e IMAGE 2 (pedra).
 */
@Component
public class OpenAiRestClientImageEditModel implements ImageEditModel {

  private static final String EDITS_PATH = "/v1/images/edits";

  private final RestClient restClient;

  /**
   * Construtor.
   *
   * @param restClient cliente HTTP autenticado para a OpenAI (bean {@code imageRestClient})
   */
  public OpenAiRestClientImageEditModel(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public ImageResponse call(ImageEditPrompt prompt) {
    JsonNode raw;
    try {
      raw =
          restClient
              .post()
              .uri(EDITS_PATH)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(multipartDe(prompt))
              .retrieve()
              .body(JsonNode.class);
    } catch (RestClientResponseException e) {
      throw new AiImageException(
          e.getClass().getSimpleName()
              + " ["
              + e.getStatusCode()
              + "]: "
              + e.getResponseBodyAsString(),
          e);
    } catch (Exception e) {
      throw new AiImageException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }
    if (raw == null) {
      throw new AiImageException("resposta vazia da OpenAI");
    }
    return respostaDe(raw);
  }

  /** Monta o multipart a partir do prompt, preservando a ordem das imagens de entrada. */
  private static MultiValueMap<String, Object> multipartDe(ImageEditPrompt prompt) {
    AiImageOptions opts = prompt.options();
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("prompt", prompt.instructions());
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

  /** Traduz o JSON cru em {@link ImageResponse}, lancando em resposta malformada. */
  private static ImageResponse respostaDe(JsonNode raw) {
    JsonNode data = raw.path("data");
    if (!data.isArray() || data.isEmpty()) {
      throw new AiImageException("resposta sem data[0]");
    }
    JsonNode b64Node = data.get(0).path("b64_json");
    if (b64Node.isMissingNode()) {
      throw new AiImageException("resposta sem b64_json");
    }
    List<ImageGeneration> generations = new ArrayList<>();
    ImageGeneration primeira = ImageGeneration.of(Image.of(b64Node.asText()));
    generations.add(primeira);
    JsonNode usage = raw.has("usage") ? raw.get("usage") : null;
    ImageResponseMetadata metadata = new ImageResponseMetadata(usage);
    return new ImageResponse(generations, metadata, raw);
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
