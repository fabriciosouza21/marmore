package com.marmore.api.imageedit.ai;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Resposta de uma chamada de geracao de imagem. Anloga a {@code
 * org.springframework.ai.image.ImageResponse}: lista de geracoes + metadados. Imutavel (record). O
 * nome dos getters segue o contrato do Spring AI ({@code getResults}, {@code getResult}, {@code
 * getMetadata}).
 *
 * <p>Carrega tambem o {@code raw} (JSON cru retornado pelo provedor) para que camadas superiores
 * (ex.: {@code FileSystemResultWriter}) possam persistir a resposta integral.
 *
 * @param results geracoes retornadas pelo provedor (nao vazia em sucesso)
 * @param metadata metadados da resposta
 * @param raw JSON cru retornado pelo provedor
 */
public record ImageResponse(
    List<ImageGeneration> results, ImageResponseMetadata metadata, @Nullable JsonNode raw) {

  /** Construtor canonical copia defensivamente a lista. */
  public ImageResponse {
    results = List.copyOf(results);
  }

  /** Retorna a primeira geracao, ou empty se vazia. */
  public Optional<ImageGeneration> getResult() {
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  /** Retorna o base64 da primeira geracao, ou empty se nao houver geracao com imagem. */
  public Optional<String> firstB64() {
    return getResult().map(g -> g.output().b64Json());
  }
}
