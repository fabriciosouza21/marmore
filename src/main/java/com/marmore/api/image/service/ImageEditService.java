package com.marmore.api.image.service;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.EditPrompts;
import com.marmore.api.image.domain.GenerateResult;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Servico de edicao de imagem via endpoint {@code /v1/images/edits} da OpenAI. Recebe apenas os
 * bytes da imagem do ambiente; injeta prompt fixo e imagem da pedra. Nenhum caminho lanca excecao:
 * falhas viram {@link GenerateResult.Err}.
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final RestClient restClient;
  private final ImageResizer resizer;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo
   * @param restClient cliente HTTP autenticado
   * @param resizer redimensionador de imagem em memoria
   */
  public ImageEditService(ImageEditProperties props, RestClient restClient, ImageResizer resizer) {
    this.props = props;
    this.restClient = restClient;
    this.resizer = resizer;
  }

  /**
   * Gera/edita imagem a partir dos bytes do ambiente, injetando prompt fixo e imagem da pedra.
   *
   * @param ambiente bytes da foto do ambiente a ser editada
   * @return sucesso ou erro, nunca lanca
   */
  public GenerateResult generate(byte[] ambiente) {
    long start = System.nanoTime();
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      return new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start));
    }
    Resource pedra = new FileSystemResource(props.getStonePath());
    if (!pedra.exists()) {
      return new GenerateResult.Err("stone image not found: " + props.getStonePath(), ms(start));
    }
    Optional<byte[]> ambienteReduzido = resizer.resize(ambiente);
    if (ambienteReduzido.isEmpty()) {
      return new GenerateResult.Err("unable to decode input image", ms(start));
    }
    try {
      EditOptions opts = EditOptions.defaults();
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("model", opts.model());
      body.add("prompt", EditPrompts.COUNTERTOP);
      body.add("size", opts.size());
      body.add("quality", opts.quality());
      body.add("n", 1);
      body.add("image[]", new InMemoryResource(ambienteReduzido.get(), "ambiente.jpg"));
      body.add("image[]", pedra);
      if (opts.sendsFidelity()) {
        body.add("input_fidelity", opts.inputFidelity());
      }

      JsonNode raw =
          restClient
              .post()
              .uri("/v1/images/edits")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .body(JsonNode.class);

      long latency = ms(start);
      JsonNode data = raw.path("data");
      if (!data.isArray() || data.isEmpty()) {
        return new GenerateResult.Err("resposta sem data[0]", latency);
      }
      JsonNode b64Node = data.get(0).path("b64_json");
      if (b64Node.isMissingNode()) {
        return new GenerateResult.Err("resposta sem b64_json", latency);
      }
      JsonNode usage = raw.has("usage") ? raw.get("usage") : null;
      return new GenerateResult.Ok(b64Node.asText(), raw, usage, latency);
    } catch (Exception e) {
      return new GenerateResult.Err(
          e.getClass().getSimpleName() + ": " + e.getMessage(), ms(start));
    }
  }

  private static long ms(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  /** ByteArrayResource com nome de arquivo, necessario para multipart. */
  private static final class InMemoryResource
      extends org.springframework.core.io.ByteArrayResource {
    private final String filename;

    InMemoryResource(byte[] bytes, String filename) {
      super(bytes);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
