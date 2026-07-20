package com.marmore.api.image.service;

import com.marmore.api.image.config.ImageEditProperties;
import com.marmore.api.image.domain.EditOptions;
import com.marmore.api.image.domain.GenerateResult;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Servico de edicao de imagem via endpoint {@code /v1/images/edits} da OpenAI. Nenhum caminho lanca
 * excecao: falhas viram {@link GenerateResult.Err}.
 */
@Service
public class ImageEditService {

  private final ImageEditProperties props;
  private final RestClient restClient;

  /**
   * Construtor.
   *
   * @param props propriedades do modulo
   * @param restClient cliente HTTP autenticado
   */
  public ImageEditService(ImageEditProperties props, RestClient restClient) {
    this.props = props;
    this.restClient = restClient;
  }

  /**
   * Gera/edita imagem a partir de um prompt e imagens de referencia.
   *
   * @param prompt prompt de edicao
   * @param images imagens de entrada (1+)
   * @param opts opcoes da chamada
   * @return sucesso ou erro, nunca lanca
   */
  public GenerateResult generate(String prompt, List<Resource> images, EditOptions opts) {
    long start = System.nanoTime();
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      return new GenerateResult.Err("OPENAI_API_KEY ausente. Defina no ambiente.", ms(start));
    }
    for (Resource img : images) {
      if (!img.exists()) {
        return new GenerateResult.Err("imagem de entrada ausente: " + img.getFilename(), ms(start));
      }
    }
    try {
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("model", opts.model());
      body.add("prompt", prompt);
      body.add("size", opts.size());
      body.add("quality", opts.quality());
      body.add("n", 1);
      for (Resource img : images) {
        body.add("image[]", img);
      }
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
}
